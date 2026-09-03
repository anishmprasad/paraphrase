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

    suspend fun paraphrase(text: String, style: Style = prefs.style): String =
        withContext(Dispatchers.IO) {
            val input = text.trim()
            if (input.isEmpty()) throw ParaphraseException("Nothing to paraphrase.")
            if (input.length > MAX_CHARS) {
                throw ParaphraseException("Selection is too long (max $MAX_CHARS characters).")
            }

            val provider = prefs.provider
            fellBackFromDevice = false

            if (provider == Provider.ON_DEVICE_AI) {
                OnDeviceAi.rewrite(context, input, style)?.let { return@withContext it }
                // No AICore, model not downloaded, or text too long: use the
                // phrase rewriter rather than showing an error for something
                // the user never chose to configure.
                fellBackFromDevice = true
                OnDeviceAi.startDownload(context)
                return@withContext OfflineRewriter.rewrite(input, style)
            }

            if (provider == Provider.LOCAL || usingFallback) {
                return@withContext OfflineRewriter.rewrite(input, style)
            }

            val raw = when {
                provider == Provider.GEMINI -> callGemini(input, style)
                else -> callOpenAiCompatible(input, style, prefs.baseUrl)
            }
            ResponseParser.clean(raw, input)
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

    private fun callGemini(text: String, style: Style): String {
        val model = prefs.model
        val url = "${prefs.baseUrl}/models/$model:generateContent"
        val body = GeminiRequest.body(model, systemPrompt(style), text)
        val response = post(url, body.toString(), mapOf("x-goog-api-key" to prefs.apiKey))
        return ResponseParser.gemini(response)
    }

    private fun callOpenAiCompatible(text: String, style: Style, base: String): String {
        val url = base.trimEnd('/') + "/chat/completions"
        val body = JSONObject()
            .put("model", prefs.model)
            .put("temperature", 0.7)
            .put("n", 1)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(style)))
                    .put(JSONObject().put("role", "user").put("content", text))
            )

        val key = prefs.apiKey
        val headers = if (key.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $key")
        val response = post(url, body.toString(), headers)
        return ResponseParser.openAi(response)
    }

    // ---------------------------------------------------------------- transport

    private fun post(url: String, payload: String, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
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
    }
}
