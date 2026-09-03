package com.paraphase.app

/**
 * A small, deterministic rewriter used when no API key is configured, or when
 * the user deliberately picks the on-device provider. It is NOT an AI model —
 * it does phrase and synonym substitution plus a few style rules, so it will
 * never match Gemini/Groq output. It exists so the app still does something
 * useful offline and on first launch.
 */
object OfflineRewriter {

    private val phrases = listOf(
        "in order to" to "to",
        "due to the fact that" to "because",
        "at this point in time" to "now",
        "at the present time" to "now",
        "in the event that" to "if",
        "a large number of" to "many",
        "a great deal of" to "much",
        "for the purpose of" to "for",
        "with regard to" to "about",
        "with respect to" to "about",
        "in spite of the fact that" to "although",
        "as a matter of fact" to "in fact",
        "in the near future" to "soon",
        "on a daily basis" to "daily",
        "is able to" to "can",
        "are able to" to "can",
        "has the ability to" to "can",
        "make a decision" to "decide",
        "give assistance to" to "help",
        "take into consideration" to "consider",
        "come to the conclusion" to "conclude",
        "in my opinion" to "I think",
        "please be advised that" to "",
        "it should be noted that" to "",
        "prior to" to "before",
        "in the process of" to ""
    )

    private val synonyms = mapOf(
        "utilize" to "use", "utilise" to "use", "commence" to "begin", "terminate" to "end",
        "obtain" to "get", "purchase" to "buy", "require" to "need", "assist" to "help",
        "attempt" to "try", "demonstrate" to "show", "sufficient" to "enough",
        "additional" to "more", "numerous" to "many", "approximately" to "about",
        "subsequently" to "later", "previously" to "earlier", "currently" to "now",
        "however" to "but", "therefore" to "so", "furthermore" to "also",
        "moreover" to "also", "nevertheless" to "still", "regarding" to "about",
        "difficult" to "hard", "important" to "key", "quickly" to "fast",
        "happy" to "glad", "enormous" to "huge", "tiny" to "small",
        "endeavour" to "try", "endeavor" to "try", "ascertain" to "find out",
        "facilitate" to "help", "initiate" to "start", "modify" to "change",
        "provide" to "give", "receive" to "get"
    )

    private val fillers = listOf(
        "actually", "basically", "really", "very", "just", "quite",
        "literally", "simply", "definitely", "totally"
    )

    private val contractions = mapOf(
        "do not" to "don't", "does not" to "doesn't", "did not" to "didn't",
        "cannot" to "can't", "can not" to "can't", "will not" to "won't",
        "is not" to "isn't", "are not" to "aren't", "was not" to "wasn't",
        "it is" to "it's", "that is" to "that's", "I am" to "I'm",
        "we are" to "we're", "you are" to "you're", "they are" to "they're",
        "I will" to "I'll", "we will" to "we'll", "I have" to "I've"
    )

    fun rewrite(text: String, style: Style): String {
        var out = text

        out = replaceAllPhrases(out, phrases)

        out = when (style) {
            Style.FORMAL -> {
                var s = replaceAllPhrases(out, contractions.map { it.value to it.key })
                s = dropWords(s, fillers)
                swapWords(s, synonyms.filterValues { it.length >= 4 })
            }
            Style.CASUAL -> replaceAllPhrases(swapWords(out, synonyms), contractions.toList())
            Style.CONCISE -> dropWords(swapWords(out, synonyms), fillers)
            Style.SIMPLE -> swapWords(out, synonyms)
            Style.EXPAND, Style.FLUENT, Style.STANDARD -> swapWords(out, synonyms)
        }

        return tidy(out)
    }

    private fun replaceAllPhrases(text: String, pairs: List<Pair<String, String>>): String {
        var out = text
        for ((from, to) in pairs) {
            out = Regex("\\b${Regex.escape(from)}\\b", RegexOption.IGNORE_CASE)
                .replace(out) { match -> matchCase(match.value, to) }
        }
        return out
    }

    private fun swapWords(text: String, map: Map<String, String>): String =
        Regex("[A-Za-z']+").replace(text) { match ->
            val replacement = map[match.value.lowercase()] ?: return@replace match.value
            matchCase(match.value, replacement)
        }

    private fun dropWords(text: String, words: List<String>): String {
        var out = text
        for (word in words) {
            out = Regex("\\b${Regex.escape(word)}\\b\\s*", RegexOption.IGNORE_CASE).replace(out, "")
        }
        return out
    }

    /** Keeps the original capitalisation shape so replacements don't look pasted in. */
    private fun matchCase(original: String, replacement: String): String = when {
        replacement.isEmpty() -> ""
        original.all { it.isUpperCase() } && original.length > 1 -> replacement.uppercase()
        original.firstOrNull()?.isUpperCase() == true ->
            replacement.replaceFirstChar { it.uppercase() }
        else -> replacement
    }

    private fun tidy(text: String): String = text
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex(" ([,.;:!?])"), "$1")
        .replace(Regex("(?m)^\\s+"), "")
        .trim()
}
