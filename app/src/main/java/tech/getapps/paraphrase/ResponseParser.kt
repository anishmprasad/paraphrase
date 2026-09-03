package tech.getapps.paraphrase

import org.json.JSONObject

/**
 * Pure parsing/cleanup helpers, kept free of Android APIs so they can be unit
 * tested on the JVM.
 */
object ResponseParser {

    private val PREAMBLES = listOf(
        "Here is the paraphrased text", "Here's the paraphrased text",
        "Here is the rewritten text", "Here's the rewritten text",
        "Paraphrased text", "Paraphrase", "Rewritten", "Sure!", "Sure,", "Certainly!"
    )

    /** Pulls the text of the FIRST candidate, skipping Gemini "thought" parts. */
    fun gemini(response: String): String {
        val json = JSONObject(response)
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blocked = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw ParaphraseException(
                if (blocked.isNotEmpty()) "The model refused this text ($blocked)."
                else "The model returned no result."
            )
        }
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw ParaphraseException("The model returned an empty result.")

        return buildString {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.optBoolean("thought", false)) continue
                append(part.optString("text", ""))
            }
        }
    }

    /** Pulls the first choice of an OpenAI-compatible /chat/completions response. */
    fun openAi(response: String): String {
        val choices = JSONObject(response).optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw ParaphraseException("The model returned no result.")
        }
        return choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty()
    }

    /** Models like to add "Sure, here's...", wrap results in quotes, or fence them. */
    fun clean(raw: String, original: String): String {
        var out = raw.trim()
        if (out.isEmpty()) throw ParaphraseException("The model returned an empty result.")

        if (out.startsWith("```")) {
            out = out.substringAfter('\n', "").substringBeforeLast("```").trim()
        }
        // A preamble can sit inside the quotes ("Sure! ...") or outside them, so
        // alternate between the two until the string stops shrinking.
        repeat(3) {
            val before = out
            out = stripQuotes(out, original)
            out = stripPreamble(out)
            if (out == before) return@repeat
        }

        if (out.isEmpty()) throw ParaphraseException("The model returned an empty result.")
        return out
    }

    /** Removes wrapping quotes, but only ones the original didn't have itself. */
    private fun stripQuotes(text: String, original: String): String {
        var out = text
        listOf('"' to '"', '\'' to '\'', '\u201C' to '\u201D').forEach { (open, close) ->
            if (out.length > 1 && out.first() == open && out.last() == close &&
                !(original.firstOrNull() == open && original.lastOrNull() == close)
            ) {
                out = out.substring(1, out.length - 1).trim()
            }
        }
        return out
    }

    private fun stripPreamble(text: String): String {
        var out = text
        PREAMBLES.forEach { prefix ->
            if (out.startsWith(prefix, ignoreCase = true)) {
                out = out.removeRange(0, prefix.length).trimStart(':', '-', ' ', '\n')
            }
        }
        return out
    }

    /** One SSE line from an OpenAI-compatible stream: the incremental text. */
    fun openAiDelta(line: String): String? {
        val payload = ssePayload(line) ?: return null
        return runCatching {
            JSONObject(payload).optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content")
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /** One SSE line from Gemini's streamGenerateContent, skipping thought parts. */
    fun geminiDelta(line: String): String? {
        val payload = ssePayload(line) ?: return null
        return runCatching {
            val parts = JSONObject(payload).optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return@runCatching null
            buildString {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.optBoolean("thought", false)) continue
                    append(part.optString("text", ""))
                }
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /** Strips the "data:" prefix; null for comments, blanks and the done marker. */
    private fun ssePayload(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        return payload
    }

    fun describeHttpError(code: Int, body: String): String {
        val apiMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        val hint = when (code) {
            401, 403 -> "Check your API key in Paraphrase."
            429 -> "Free-tier rate limit hit — wait a moment and try again."
            404 -> "Model not available. Clear the Model field in Paraphrase to return to the current default."
            else -> ""
        }
        return listOf("HTTP $code", apiMessage, hint).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
