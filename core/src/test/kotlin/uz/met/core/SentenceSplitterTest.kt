package uz.met.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceSplitterTest {
    @Test
    fun `emits a sentence only after seeing what follows it`() {
        val splitter = SentenceSplitter()
        assertEquals(emptyList<String>(), splitter.feed("Oh, really"))
        assertEquals(emptyList<String>(), splitter.feed("?"))
        assertEquals(listOf("Oh, really?"), splitter.feed(" What"))
        assertEquals("What", splitter.flush())
    }

    @Test
    fun `handles several sentences in one delta`() {
        val pieces = SentenceSplitter().feed("Nice! I see. Tell me more")
        assertEquals(listOf("Nice!", "I see."), pieces)
    }

    @Test
    fun `does not cut after an abbreviation or inside a number`() {
        val splitter = SentenceSplitter()
        assertEquals(emptyList<String>(), splitter.feed("Dr. Smith paid 3.5 dollars"))
        assertEquals("Dr. Smith paid 3.5 dollars", splitter.flush())
    }

    @Test
    fun `cuts a long sentence at its last pause`() {
        val text = "When I was a little boy in a very small village near the mountains, we walked to"
        val pieces = SentenceSplitter(softLimit = 80).feed(text)
        assertEquals(listOf("When I was a little boy in a very small village near the mountains,"), pieces)
    }

    @Test
    fun `flush returns null when nothing is left`() {
        assertNull(SentenceSplitter().flush())
    }
}
