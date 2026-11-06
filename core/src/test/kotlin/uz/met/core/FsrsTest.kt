package uz.met.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {
    private val day = 86_400_000L
    private val start = 1_000_000_000_000L

    @Test
    fun `a better rating schedules a new card further away`() {
        val dues = Rating.entries.map { Fsrs.review(CardMemory(), it, start).dueMs }
        assertEquals(dues.sorted(), dues)
        assertTrue(dues[0] - start < day)
    }

    @Test
    fun `successful reviews keep stretching the interval`() {
        var card = Fsrs.review(CardMemory(), Rating.GOOD, start)
        var previous = card.dueMs - start
        repeat(4) {
            val now = card.dueMs
            card = Fsrs.review(card, Rating.GOOD, now)
            assertTrue(card.dueMs - now > previous)
            previous = card.dueMs - now
        }
    }

    @Test
    fun `forgetting brings the card back soon and counts a lapse`() {
        val learned = Fsrs.review(CardMemory(), Rating.EASY, start)
        val now = learned.dueMs
        val forgotten = Fsrs.review(learned, Rating.AGAIN, now)
        assertTrue(forgotten.dueMs - now < day)
        assertTrue(forgotten.stability < learned.stability)
        assertEquals(1, forgotten.lapses)
    }

    @Test
    fun `the target retention gives an interval equal to the stability`() {
        assertEquals(10L, Fsrs.intervalDays(10.0))
    }
}
