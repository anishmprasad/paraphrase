package tech.getapps.paraphrase

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ParaphraseException(message: String) : Exception(message)

/** Turns selected text into a rewritten version. One entry point: [paraphrase]. */
class ParaphraseEngine(context: Context) {

    private val context = context.applicationContext
    private val prefs = Prefs(context)

    /**
     * True when we'll silently fall back to the offline rewriter — a cloud
     * provider with no key yet. Local servers need no key, so they never do.
     */
    val usingFallback: Boolean
        get() = prefs.provider.requiresKey && prefs.apiKey.isBlank()

    /** Set when the on-device model was asked for but could not run. */
    @Volatile
    var fellBackFromDevice: Boolean = false
        private set

    /**
     * [onPartial] receives the text so far as it streams in, on a background
     * thread. Passing null asks for a single response instead. Streaming is
     * what makes the wait watchable rather than a spinner, so the UI passes it.
     */
    suspend fun paraphrase(
        text: String,
        style: Style = prefs.style,
        onPartial: ((String) -> Unit)? = null
    ): String =
        withContext(Dispatchers.IO) {
            val input = text.trim()
            if (input.isEmpty()) throw ParaphraseException("Nothing to paraphrase.")
            if (input.length > MAX_CHARS) {
                throw ParaphraseException("Selection is too long (max $MAX_CHARS characters).")
            }

            val provider = prefs.provider
            fellBackFromDevice = false

            if (provider == Provider.ON_DEVICE_AI) {
                OnDeviceAi.rewrite(context, input, style, onPartial)?.let { return@withContext it }
                // No AICore, model not downloaded, or text too long: use the
                // phrase rewriter rather than showing an error for something
                // the user never chose to configure.
                fellBackFromDevice = true
                OnDeviceAi.startDownload(context)
                return@withContext offline(input, style, onPartial)
            }

            if (provider == Provider.LOCAL || usingFallback) {
                return@withContext offline(input, style, onPartial)
            }

            val raw = when {
                provider == Provider.GEMINI -> callGemini(input, style, onPartial)
                else -> callOpenAiCompatible(input, style, prefs.baseUrl, onPartial)
            }
            ResponseParser.clean(raw, input)
        }

    /**
     * The offline rewriter is instant, which next to a streaming provider reads
     * as "nothing happened". Feeding it out word by word keeps one behaviour
     * across every backend, briefly and without a spinner.
     */
    private fun offline(text: String, style: Style, onPartial: ((String) -> Unit)?): String {
        val result = OfflineRewriter.rewrite(text, style)
        if (onPartial == null) return result

        val words = result.split(" ")
        val pause = (OFFLINE_REVEAL_MS / words.size.coerceAtLeast(1)).coerceIn(12L, 45L)
        val shown = StringBuilder()
        words.forEachIndexed { index, word ->
            if (index > 0) shown.append(' ')
            shown.append(word)
            onPartial(shown.toString())
            Thread.sleep(pause)
        }
        return result
    }

    // ---------------------------------------------------------------- prompts

    private fun systemPrompt(style: Style) = buildString {
        append("You are a paraphrasing engine embedded in a keyboard tool. ")
        append(style.instruction)
        append(" Preserve the original meaning, the original language, and any names, numbers, ")
        append("URLs, code, emoji or @mentions exactly as they appear. ")
        append("Keep the original leading/trailing punctuation style. ")
        append("Reply with ONLY the rewritten text — no quotes, no preamble, no explanation, ")
        append("no options, no markdown formatting.")
    }

    // ---------------------------------------------------------------- backends

    private fun callGemini(text: String, style: Style, onPartial: ((String) -> Unit)?): String {
        val model = prefs.model
        val headers = mapOf("x-goog-api-key" to prefs.apiKey)
        val body = GeminiRequest.body(model, systemPrompt(style), text).toString()

        val plainUrl = "${prefs.baseUrl}/models/$model:generateContent"
        if (onPartial != null) {
            val streamUrl = "${prefs.baseUrl}/models/$model:streamGenerateContent?alt=sse"
            streamOrNull(
                streamUrl, body, headers, onPartial,
                ResponseParser::geminiDelta, ResponseParser::gemini
            )?.let { return it }
        }
        return ResponseParser.gemini(post(plainUrl, body, headers))
    }

    private fun callOpenAiCompatible(
        text: String,
        style: Style,
        base: String,
        onPartial: ((String) -> Unit)?
    ): String {
        val url = base.trimEnd('/') + "/chat/completions"
        val body = JSONObject()
            .put("model", prefs.model)
            .put("temperature", 0.7)
            .put("n", 1)
            .apply { if (onPartial != null) put("stream", true) }
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(style)))
                    .put(JSONObject().put("role", "user").put("content", text))
            )

        val key = prefs.apiKey
        val headers = if (key.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $key")
        if (onPartial != null) {
            streamOrNull(
                url, body.toString(), headers, onPartial,
                ResponseParser::openAiDelta, ResponseParser::openAi
            )?.let { return it }
        }
        // Retry without the stream flag: some endpoints reject it outright.
        body.remove("stream")
        return ResponseParser.openAi(post(url, body.toString(), headers))
    }

    // ---------------------------------------------------------------- transport

    /**
     * Attempts a streamed request. Returns null when streaming is not usable —
     * the endpoint rejected it, ignored it, or produced nothing — so the caller
     * can fall back to a single response. Never throws for those cases: the
     * fallback request is what surfaces a real error.
     */
    private fun streamOrNull(
        url: String,
        payload: String,
        headers: Map<String, String>,
        onPartial: (String) -> Unit,
        parseDelta: (String) -> String?,
        parseWhole: (String) -> String
    ): String? {
        val connection = openConnection(url, headers).apply {
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null

            if (!ResponseParser.isEventStream(connection.contentType)) {
                // Accepted the flag, answered with one JSON body anyway.
                val whole = connection.inputStream.bufferedReader(StandardCharsets.UTF_8)
                    .use(BufferedReader::readText)
                return runCatching { parseWhole(whole) }.getOrNull()
                    ?.also(onPartial)
            }

            val accumulated = StringBuilder()
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val delta = parseDelta(line) ?: continue
                    accumulated.append(delta)
                    onPartial(accumulated.toString())
                }
            }
            return accumulated.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, headers: Map<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    private fun post(url: String, payload: String, headers: Map<String, String>): String {
        val connection = openConnection(url, headers)
        try {
            connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)
                ?.use(BufferedReader::readText)
                .orEmpty()
            if (code !in 200..299) throw ParaphraseException(ResponseParser.describeHttpError(code, text))
            return text
        } catch (e: ParaphraseException) {
            throw e
        } catch (e: Exception) {
            throw ParaphraseException("Network error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_CHARS = 8000

        /** Total time to reveal an offline rewrite; short enough not to annoy. */
        const val OFFLINE_REVEAL_MS = 600L
    }
}
