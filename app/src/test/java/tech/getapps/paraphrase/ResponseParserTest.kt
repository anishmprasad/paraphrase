package tech.getapps.paraphrase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {

    @Test
    fun `gemini takes the first candidate and skips thought parts`() {
        val json = """
            {"candidates":[
              {"content":{"parts":[
                 {"text":"internal reasoning","thought":true},
                 {"text":"The meeting was pushed back."}]}},
              {"content":{"parts":[{"text":"second candidate"}]}}
            ]}
        """.trimIndent()
        assertEquals("The meeting was pushed back.", ResponseParser.gemini(json))
    }

    @Test
    fun `gemini surfaces a safety block`() {
        val json = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        val error = runCatching { ResponseParser.gemini(json) }.exceptionOrNull()
        assertTrue(error is ParaphraseException)
        assertTrue(error!!.message!!.contains("SAFETY"))
    }

    @Test
    fun `openAi takes the first choice`() {
        val json = """
            {"choices":[{"message":{"role":"assistant","content":"Let's meet later."}},
                        {"message":{"content":"ignored"}}]}
        """.trimIndent()
        assertEquals("Let's meet later.", ResponseParser.openAi(json))
    }

    @Test
    fun `clean strips preambles, fences and added quotes`() {
        assertEquals("Hello there.", ResponseParser.clean("Sure! Hello there.", "hi"))
        assertEquals("Hello there.", ResponseParser.clean("\"Hello there.\"", "hi"))
        assertEquals("Hello there.", ResponseParser.clean("```\nHello there.\n```", "hi"))
        assertEquals(
            "Hello there.",
            ResponseParser.clean("Here is the paraphrased text: Hello there.", "hi")
        )
    }

    @Test
    fun `clean strips a preamble hiding inside quotes`() {
        assertEquals("Hello there.", ResponseParser.clean("\"Sure! Hello there.\"", "hi"))
    }

    @Test
    fun `clean keeps quotes the original already had`() {
        assertEquals("\"Stop\"", ResponseParser.clean("\"Stop\"", "\"Halt\""))
    }

    @Test
    fun `clean rejects an empty result`() {
        assertTrue(runCatching { ResponseParser.clean("   ", "hi") }.exceptionOrNull() is ParaphraseException)
    }

    @Test
    fun `openAi stream deltas are read, control lines ignored`() {
        assertEquals(
            "Hello",
            ResponseParser.openAiDelta("""data: {"choices":[{"delta":{"content":"Hello"}}]}""")
        )
        assertEquals(null, ResponseParser.openAiDelta("data: [DONE]"))
        assertEquals(null, ResponseParser.openAiDelta(""))
        assertEquals(null, ResponseParser.openAiDelta(": keep-alive comment"))
        // A role-only first chunk carries no text.
        assertEquals(
            null,
            ResponseParser.openAiDelta("""data: {"choices":[{"delta":{"role":"assistant"}}]}""")
        )
    }

    @Test
    fun `gemini stream deltas are read and thought parts skipped`() {
        assertEquals(
            "Hi there",
            ResponseParser.geminiDelta(
                """data: {"candidates":[{"content":{"parts":[{"text":"Hi there"}]}}]}"""
            )
        )
        assertEquals(
            null,
            ResponseParser.geminiDelta(
                """data: {"candidates":[{"content":{"parts":[{"text":"thinking","thought":true}]}}]}"""
            )
        )
        assertEquals(null, ResponseParser.geminiDelta("data: [DONE]"))
    }

    @Test
    fun `only a real event-stream content type counts as streaming`() {
        assertTrue(ResponseParser.isEventStream("text/event-stream"))
        assertTrue(ResponseParser.isEventStream("text/event-stream; charset=utf-8"))
        assertTrue(ResponseParser.isEventStream("TEXT/EVENT-STREAM"))
        // An endpoint that accepts stream:true and answers with plain JSON.
        assertFalse(ResponseParser.isEventStream("application/json"))
        assertFalse(ResponseParser.isEventStream(null))
        assertFalse(ResponseParser.isEventStream(""))
    }

    @Test
    fun `malformed stream lines never throw`() {
        assertEquals(null, ResponseParser.openAiDelta("data: {not json"))
        assertEquals(null, ResponseParser.geminiDelta("data: {not json"))
    }

    @Test
    fun `http errors carry an actionable hint`() {
        val message = ResponseParser.describeHttpError(401, """{"error":{"message":"Invalid key"}}""")
        assertTrue(message.contains("HTTP 401"))
        assertTrue(message.contains("Invalid key"))
        assertTrue(message.contains("API key"))
    }
}
