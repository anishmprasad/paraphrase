package tech.getapps.paraphrase

/**
 * Backends that have a usable free tier. Gemini and Groq both hand out a key
 * from a normal sign-in with no card on file; OPENAI_COMPAT covers anything
 * that speaks the /chat/completions shape (OpenRouter's free models, a local
 * llama.cpp server, a company gateway...).
 */
enum class Provider(
    val id: String,
    val label: String,
    val defaultModel: String,
    val keyUrl: String,
    val note: String
) {
    GEMINI(
        id = "gemini",
        label = "Google Gemini (free tier)",
        defaultModel = "gemini-3.8-flash",
        keyUrl = "https://aistudio.google.com/app/apikey",
        note = "Free tier, no card required. Recommended."
    ),
    GROQ(
        id = "groq",
        label = "Groq (free tier)",
        defaultModel = "llama-3.3-70b-versatile",
        keyUrl = "https://console.groq.com/keys",
        note = "Very fast. Free tier with daily limits."
    ),
    OPENAI_COMPAT(
        id = "openai",
        label = "OpenAI-compatible endpoint",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        keyUrl = "https://openrouter.ai/keys",
        note = "Any /chat/completions API — OpenRouter free models, a gateway, or your own server."
    ),
    LOCAL(
        id = "local",
        label = "On-device basic rewriter (no key, no network)",
        defaultModel = "-",
        keyUrl = "",
        note = "Works offline with no account, but it only does simple word and phrase swaps."
    );

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

enum class Style(val id: String, val label: String, val instruction: String) {
    STANDARD("standard", "Standard", "Rewrite it naturally, keeping the same level of formality and roughly the same length."),
    FLUENT("fluent", "Fluent / fix grammar", "Fix grammar, spelling and awkward phrasing so it reads like fluent, natural writing. Change as little as possible otherwise."),
    FORMAL("formal", "Formal", "Rewrite it in polished, professional language suitable for business email. No slang or contractions."),
    CASUAL("casual", "Casual", "Rewrite it in a relaxed, friendly, conversational tone, as if messaging a colleague you know well."),
    CONCISE("concise", "Concise", "Rewrite it as briefly as possible while keeping every essential point. Cut filler words."),
    EXPAND("expand", "Expand", "Rewrite it with more detail and clearer explanation, roughly 1.5 to 2 times the original length. Do not invent facts."),
    SIMPLE("simple", "Simple English", "Rewrite it in plain, simple language that a 12-year-old could follow. Short sentences, common words.");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}
