package uz.met.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class SpeechMetricsTest {
    private val rate = 16_000

    private fun tone(seconds: Double) = FloatArray((seconds * rate).toInt()) { i -> 0.3f * sin(i * 0.1f) }
    private fun silence(seconds: Double) = FloatArray((seconds * rate).toInt())

    @Test
    fun `word comparison marks what was missed`() {
        val result = compareWords("I went to the market.", "I went to market")
        assertEquals(listOf(true, true, true, false, true), result.map { it.matched })
        assertEquals("market.", result.last().word)
        assertEquals(80, matchScore(result))
    }

    @Test
    fun `extra spoken words do not count against the expected ones`() {
        val result = compareWords("I like tea", "well I really like green tea")
        assertTrue(result.all { it.matched })
    }

    @Test
    fun `phrase matching uses whole words and ignores case`() {
        assertTrue(containsPhrase("I'd Like To LOOK AFTER them", "look after"))
        assertFalse(containsPhrase("I overlook after this", "look"))
    }

    @Test
    fun `an answer matches its accepted variants`() {
        assertTrue(matchesAny("She doesn't like coffee.", listOf("she does not like coffee", "she doesn't like coffee")))
        assertFalse(matchesAny("she don't like coffee", listOf("she doesn't like coffee")))
    }

    @Test
    fun `pauses are counted inside speech only`() {
        val samples = silence(1.0) + tone(1.0) + silence(0.8) + tone(1.0) + silence(0.2) + tone(1.0) + silence(2.0)
        val stats = AudioAnalysis.stats(samples, rate)
        assertEquals(1, stats.pauses)
        assertEquals(3.0, stats.speechSeconds, 0.1)
    }

    @Test
    fun `silence has no speech`() {
        assertEquals(AudioStats(0.0, 0), AudioAnalysis.stats(silence(2.0), rate))
    }

    @Test
    fun `short audio stays in one piece`() {
        assertEquals(1, AudioAnalysis.splitForWhisper(tone(10.0), rate).size)
        assertEquals(0, AudioAnalysis.splitForWhisper(FloatArray(0), rate).size)
    }

    @Test
    fun `long audio is cut in a quiet place and nothing is lost`() {
        val samples = tone(22.0) + silence(0.6) + tone(20.0)
        val pieces = AudioAnalysis.splitForWhisper(samples, rate)
        assertEquals(2, pieces.size)
        val cutSeconds = pieces[0].last.toDouble() / rate
        assertTrue("cut at $cutSeconds", cutSeconds in 21.9..22.7)
        assertEquals(samples.size, pieces.sumOf { it.count() })
    }

    @Test
    fun `audio with no silence is still cut under the limit`() {
        val pieces = AudioAnalysis.splitForWhisper(tone(70.0), rate)
        assertTrue(pieces.all { it.count() <= 25 * rate })
        assertEquals((70.0 * rate).toInt(), pieces.sumOf { it.count() })
    }
}
