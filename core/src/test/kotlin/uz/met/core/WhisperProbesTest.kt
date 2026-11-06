package uz.met.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperProbesTest {
    private val brothers = WhisperProbe("I have two brother.", "two brother", "two brothers")

    @Test
    fun `a kept mistake is recognised despite case and punctuation`() {
        assertEquals(ProbeOutcome.KEPT, brothers.classify("I have Two Brother."))
    }

    @Test
    fun `matching respects word boundaries`() {
        assertEquals(ProbeOutcome.CORRECTED, brothers.classify("I have two brothers."))
    }

    @Test
    fun `something else entirely counts as misheard`() {
        assertEquals(ProbeOutcome.MISHEARD, brothers.classify("I have to bother."))
    }

    @Test
    fun `curly apostrophes match straight ones`() {
        val probe = WhisperProbe("She don't like coffee.", "she don't", "she doesn't")
        assertEquals(ProbeOutcome.KEPT, probe.classify("She don’t like coffee"))
    }

    @Test
    fun `a number written as digits does not hide a kept mistake`() {
        val probe = WhisperProbes.all.first { it.spoken.startsWith("I live in Tashkent") }
        assertEquals(ProbeOutcome.KEPT, probe.classify("I live in Tascant since 5 years."))
    }

    @Test
    fun `every sentence keeps its own mistake when transcribed exactly`() {
        WhisperProbes.all.forEach { assertEquals(it.spoken, ProbeOutcome.KEPT, it.classify(it.spoken)) }
    }

    @Test
    fun `there are twenty sentences`() {
        assertEquals(20, WhisperProbes.all.size)
    }
}
