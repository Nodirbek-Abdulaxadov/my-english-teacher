package uz.met.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun daysAgo(vararg days: Long) = days.map { today.minusDays(it) }.toSet()

    @Test
    fun `no days means no streak`() {
        assertEquals(StreakInfo(0, 0, 0, false), Streak.compute(emptySet(), today))
    }

    @Test
    fun `today not done yet does not break the streak`() {
        val info = Streak.compute(daysAgo(1, 2, 3), today)
        assertEquals(3, info.current)
        assertFalse(info.goalMetToday)
    }

    @Test
    fun `today done adds to the streak`() {
        val info = Streak.compute(daysAgo(0, 1, 2), today)
        assertEquals(3, info.current)
        assertTrue(info.goalMetToday)
    }

    @Test
    fun `a missed day without a freeze resets the streak but keeps the best`() {
        val info = Streak.compute(daysAgo(0, 1, 3, 4, 5), today)
        assertEquals(2, info.current)
        assertEquals(3, info.best)
    }

    @Test
    fun `seven days earn a freeze that covers one missed day`() {
        val info = Streak.compute(daysAgo(0, 2, 3, 4, 5, 6, 7, 8), today)
        assertEquals(8, info.current)
        assertEquals(0, info.freezes)
    }

    @Test
    fun `a freeze does not cover two missed days`() {
        val info = Streak.compute(daysAgo(0, 3, 4, 5, 6, 7, 8, 9), today)
        assertEquals(1, info.current)
    }

    @Test
    fun `speaking earns the most`() {
        assertEquals(6, Xp.forSpeaking(60.0))
    }
}
