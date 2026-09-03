package tech.getapps.paraphrase

/**
 * Backends with a genuinely free path — a free tier, free models, or your own
 * machine. All of them except Gemini speak the OpenAI /chat/completions shape,
 * so a preset is really just a base URL, a default model, and where to get a key.
 *
 * Model ids drift as providers retire them. Every field here is editable in the
 * app, and a 404 tells the user to clear the model field to return to the
 * default, so a stale preset is recoverable rather than fatal.
 */
enum class Provider(
    val id: String,
    val label: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val keyUrl: String,
    val note: String,
    /** false for local servers, which authenticate nothing. */
    val requiresKey: Boolean = true,
    /** true where the user must point us at their own machine. */
    val editableBaseUrl: Boolean = false
) {
    GEMINI(
        id = "gemini",
        label = "Google Gemini (free tier)",
        defaultModel = "gemini-3.8-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        keyUrl = "https://aistudio.google.com/app/apikey",
        note = "Free tier, no card required. Recommended."
    ),
    GROQ(
        id = "groq",
        label = "Groq (free tier)",
        defaultModel = "llama-3.3-70b-versatile",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        keyUrl = "https://console.groq.com/keys",
        note = "Free tier with daily limits. The fastest of these by some margin."
    ),
    CEREBRAS(
        id = "cerebras",
        label = "Cerebras (free tier)",
        defaultModel = "llama-3.3-70b",
        defaultBaseUrl = "https://api.cerebras.ai/v1",
        keyUrl = "https://cloud.cerebras.ai",
        note = "Around a million free tokens a day. Very fast."
    ),
    MISTRAL(
        id = "mistral",
        label = "Mistral (free tier)",
        defaultModel = "mistral-small-latest",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        keyUrl = "https://console.mistral.ai/api-keys",
        note = "Large free monthly quota, but the free tier requires opting in to training on your data."
    ),
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter (free models)",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        keyUrl = "https://openrouter.ai/keys",
        note = "One key, many models. Any model id ending in \":free\" costs nothing."
    ),
    HUGGINGFACE(
        id = "huggingface",
        label = "Hugging Face (free tier)",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct",
        defaultBaseUrl = "https://router.huggingface.co/v1",
        keyUrl = "https://huggingface.co/settings/tokens",
        note = "Rate limited and on shared hardware, so speed varies."
    ),
    OLLAMA(
        id = "ollama",
        label = "Ollama on your own machine",
        defaultModel = "llama3.2",
        defaultBaseUrl = "http://192.168.1.10:11434/v1",
        keyUrl = "https://ollama.com/download",
        note = "Free and private — nothing leaves your network. Put your computer's LAN IP in the URL, and start Ollama with OLLAMA_HOST=0.0.0.0 so the phone can reach it.",
        requiresKey = false,
        editableBaseUrl = true
    ),
    LM_STUDIO(
        id = "lmstudio",
        label = "LM Studio on your own machine",
        defaultModel = "local-model",
        defaultBaseUrl = "http://192.168.1.10:1234/v1",
        keyUrl = "https://lmstudio.ai",
        note = "Same idea as Ollama. Enable the local server, tick \"serve on network\", and use your computer's LAN IP.",
        requiresKey = false,
        editableBaseUrl = true
    ),
    OPENAI_COMPAT(
        id = "openai",
        label = "Other OpenAI-compatible endpoint",
        defaultModel = "gpt-4o-mini",
        defaultBaseUrl = "https://api.openai.com/v1",
        keyUrl = "",
        note = "Anything that speaks /chat/completions — a company gateway, llama.cpp, vLLM, or a provider not listed here.",
        requiresKey = false,
        editableBaseUrl = true
    ),
    LOCAL(
        id = "local",
        label = "On-device basic rewriter (no key, no network)",
        defaultModel = "-",
        defaultBaseUrl = "",
        keyUrl = "",
        note = "Works offline with no account, but it only does simple word and phrase swaps.",
        requiresKey = false
    );

    /** Gemini has its own request shape; everything else is OpenAI-compatible. */
    val isOpenAiCompatible: Boolean get() = this != GEMINI && this != LOCAL

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
