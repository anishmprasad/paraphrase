package tech.getapps.paraphrase

import com.google.mlkit.genai.rewriting.RewriterOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class OnDeviceAiTest {

    @Test
    fun `styles map onto the rewrites Gemini Nano actually offers`() {
        assertEquals(RewriterOptions.OutputType.ELABORATE, OnDeviceAi.outputType(Style.EXPAND))
        assertEquals(RewriterOptions.OutputType.SHORTEN, OnDeviceAi.outputType(Style.CONCISE))
        assertEquals(RewriterOptions.OutputType.FRIENDLY, OnDeviceAi.outputType(Style.CASUAL))
        assertEquals(RewriterOptions.OutputType.PROFESSIONAL, OnDeviceAi.outputType(Style.FORMAL))
    }

    @Test
    fun `styles with no equivalent fall back to a plain rephrase`() {
        listOf(Style.STANDARD, Style.FLUENT, Style.SIMPLE).forEach {
            assertEquals(RewriterOptions.OutputType.REPHRASE, OnDeviceAi.outputType(it))
        }
    }

    @Test
    fun `every style maps to something`() {
        Style.entries.forEach { OnDeviceAi.outputType(it) }
    }
}
