package tech.getapps.paraphrase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRequestTest {

    @Test
    fun `gemini 3 and later count as modern`() {
        assertTrue(GeminiRequest.isModern("gemini-3.8-flash"))
        assertTrue(GeminiRequest.isModern("gemini-3.6-flash"))
        assertTrue(GeminiRequest.isModern("gemini-4-flash"))
        assertFalse(GeminiRequest.isModern("gemini-2.5-flash"))
        assertFalse(GeminiRequest.isModern("gemini-1.5-pro"))
    }

    @Test
    fun `unknown model names are treated as modern`() {
        assertTrue(GeminiRequest.isModern("some-future-model"))
    }

    @Test
    fun `3x config uses a thinking level and drops deprecated sampling fields`() {
        val config = GeminiRequest.generationConfig("gemini-3.8-flash")
        assertEquals("low", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        // temperature/topP/topK/candidateCount are rejected by 3.x models.
        assertFalse(config.has("temperature"))
        assertFalse(config.has("topP"))
        assertFalse(config.has("candidateCount"))
    }

    @Test
    fun `2x config keeps temperature and the old thinking budget`() {
        val config = GeminiRequest.generationConfig("gemini-2.5-flash")
        assertEquals(0.7, config.getDouble("temperature"), 0.001)
        assertEquals(0, config.getJSONObject("thinkingConfig").getInt("thinkingBudget"))
        assertFalse(config.getJSONObject("thinkingConfig").has("thinkingLevel"))
    }

    @Test
    fun `body carries the system prompt and the user text`() {
        val body = GeminiRequest.body("gemini-3.8-flash", "SYSTEM", "hello")
        val system = body.getJSONObject("systemInstruction")
            .getJSONArray("parts").getJSONObject(0).getString("text")
        val user = body.getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("SYSTEM", system)
        assertEquals("hello", user)
        assertEquals("user", body.getJSONArray("contents").getJSONObject(0).getString("role"))
    }
}
