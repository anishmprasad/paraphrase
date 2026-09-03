package tech.getapps.paraphrase

import android.content.Context

/** Everything the app remembers, in one place. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("paraphrase", Context.MODE_PRIVATE)

    init {
        migrate()
    }

    /**
     * The model name is persisted the first time settings are saved, so a new
     * default alone would not reach anyone who already ran the app. Drop the
     * stored value when it names a model the provider has since retired, which
     * makes the getter fall back to the current default.
     */
    private fun migrate() {
        if (sp.getInt(KEY_SCHEMA, 1) >= SCHEMA) return
        Provider.entries.forEach { provider ->
            val stored = sp.getString(KEY_MODEL + provider.id, null)
            if (stored != null && stored in RETIRED_MODELS) {
                sp.edit().remove(KEY_MODEL + provider.id).apply()
            }
        }
        sp.edit().putInt(KEY_SCHEMA, SCHEMA).apply()
    }

    var provider: Provider
        get() = Provider.fromId(sp.getString(KEY_PROVIDER, null))
        set(value) = sp.edit().putString(KEY_PROVIDER, value.id).apply()

    /** API keys are kept per provider so switching back and forth doesn't lose them. */
    var apiKey: String
        get() = sp.getString(keyFor(provider), "").orEmpty()
        set(value) = sp.edit().putString(keyFor(provider), value.trim()).apply()

    var model: String
        get() = sp.getString(KEY_MODEL + provider.id, null) ?: provider.defaultModel
        set(value) = sp.edit()
            .putString(KEY_MODEL + provider.id, value.trim().ifEmpty { provider.defaultModel })
            .apply()

    var baseUrl: String
        get() = sp.getString(KEY_BASE_URL, null) ?: "https://openrouter.ai/api/v1"
        set(value) = sp.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()

    var style: Style
        get() = Style.fromId(sp.getString(KEY_STYLE, null))
        set(value) = sp.edit().putString(KEY_STYLE, value.id).apply()

    /** true = replace the selection immediately; false = show a preview first. */
    var instantReplace: Boolean
        get() = sp.getBoolean(KEY_INSTANT, true)
        set(value) = sp.edit().putBoolean(KEY_INSTANT, value).apply()

    /** The landing page is shown once, then on demand from the setup screen. */
    var seenLanding: Boolean
        get() = sp.getBoolean(KEY_SEEN_LANDING, false)
        set(value) = sp.edit().putBoolean(KEY_SEEN_LANDING, value).apply()

    fun hasKey(): Boolean = provider == Provider.LOCAL || apiKey.isNotBlank()

    private fun keyFor(p: Provider) = KEY_API + p.id

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_API = "api_key_"
        const val KEY_MODEL = "model_"
        const val KEY_BASE_URL = "base_url"
        const val KEY_STYLE = "style"
        const val KEY_INSTANT = "instant_replace"
        const val KEY_SEEN_LANDING = "seen_landing"
        const val KEY_SCHEMA = "schema"

        /** Bump alongside RETIRED_MODELS to re-run the migration. */
        const val SCHEMA = 2

        /** Models the provider no longer serves to new keys. */
        val RETIRED_MODELS = setOf(
            "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro",
            "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro"
        )
    }
}
