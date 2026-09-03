package tech.getapps.paraphrase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTest {

    @Test
    fun `every remote preset has a usable base url and model`() {
        Provider.entries.filter { it != Provider.LOCAL }.forEach { provider ->
            assertTrue(
                "${provider.id} needs a base URL",
                provider.defaultBaseUrl.startsWith("http")
            )
            assertFalse("${provider.id} needs a model", provider.defaultModel.isBlank())
            assertFalse(
                "${provider.id} base URL should not end in a slash",
                provider.defaultBaseUrl.endsWith("/")
            )
        }
    }

    @Test
    fun `hosted providers are https, only self-hosted ones are plain http`() {
        Provider.entries.filter { it.defaultBaseUrl.startsWith("http://") }.forEach {
            assertTrue("${it.id} is cleartext, so it must be a self-hosted preset", it.editableBaseUrl)
        }
    }

    @Test
    fun `local servers need no key`() {
        assertFalse(Provider.OLLAMA.requiresKey)
        assertFalse(Provider.LM_STUDIO.requiresKey)
        assertTrue(Provider.GEMINI.requiresKey)
        assertTrue(Provider.GROQ.requiresKey)
        assertTrue(Provider.CEREBRAS.requiresKey)
    }

    @Test
    fun `gemini is the only one that is not openai compatible`() {
        Provider.entries.forEach { provider ->
            val expected = provider != Provider.GEMINI && provider != Provider.LOCAL
            assertTrue(provider.id, provider.isOpenAiCompatible == expected)
        }
    }

    @Test
    fun `ids are unique and stable`() {
        assertTrue(Provider.entries.map { it.id }.toSet().size == Provider.entries.size)
        // ids are persisted in SharedPreferences, so renaming one silently
        // resets that user's provider choice.
        assertTrue(Provider.fromId("gemini") == Provider.GEMINI)
        assertTrue(Provider.fromId("ollama") == Provider.OLLAMA)
        assertTrue(Provider.fromId("nonsense") == Provider.GEMINI)
    }
}
