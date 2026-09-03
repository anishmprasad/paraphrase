package com.paraphase.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OfflineRewriterTest {

    @Test
    fun `collapses wordy phrases`() {
        assertEquals(
            "To use the system, you must get approval because it is now restricted.",
            OfflineRewriter.rewrite(
                "In order to utilize the system, you must obtain approval due to the fact that it is currently restricted.",
                Style.STANDARD
            )
        )
    }

    @Test
    fun `concise style drops filler words`() {
        val out = OfflineRewriter.rewrite("This is basically really very important.", Style.CONCISE)
        assertFalse(out.contains("basically"))
        assertFalse(out.contains("really"))
    }

    @Test
    fun `formal style expands contractions`() {
        val out = OfflineRewriter.rewrite("I don't think it's ready.", Style.FORMAL)
        assertFalse(out.contains("don't"))
        assertFalse(out.contains("it's"))
    }

    @Test
    fun `casual style adds contractions`() {
        val out = OfflineRewriter.rewrite("I do not think that is ready.", Style.CASUAL)
        assertEquals("I don't think that's ready.", out)
    }
}
