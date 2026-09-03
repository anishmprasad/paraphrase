package com.paraphase.app

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

    private val prefs = Prefs(context)

    /** True when we'll silently fall back to the offline rewriter. */
    val usingFallback: Boolean
        get() = prefs.provider != Provider.LOCAL && prefs.apiKey.isBlank()

    suspend fun paraphrase(text: String, style: Style = prefs.style): String =
        withContext(Dispatchers.IO) {
            val input = text.trim()
            if (input.isEmpty()) throw ParaphraseException("Nothing to paraphrase.")
            if (input.length > MAX_CHARS) {
                throw ParaphraseException("Selection is too long (max $MAX_CHARS characters).")
            }

            val provider = prefs.provider
            if (provider == Provider.LOCAL || prefs.apiKey.isBlank()) {
                return@withContext OfflineRewriter.rewrite(input, style)
            }

            val raw = when (provider) {
                Provider.GEMINI -> callGemini(input, style)
                Provider.GROQ -> callOpenAiCompatible(input, style, GROQ_BASE)
                Provider.OPENAI_COMPAT -> callOpenAiCompatible(input, style, prefs.baseUrl)
                Provider.LOCAL -> ""
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
        val url = "$GEMINI_BASE/models/$model:generateContent"
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

        val response = post(url, body.toString(), mapOf("Authorization" to "Bearer ${prefs.apiKey}"))
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
        const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"
        const val GROQ_BASE = "https://api.groq.com/openai/v1"
    }
}
