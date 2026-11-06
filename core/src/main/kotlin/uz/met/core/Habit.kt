package uz.met.core

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** XP rewards. Speaking earns the most per minute, because speaking is what this app is for. */
object Xp {
    const val PER_SPEAKING_MINUTE = 6
    const val SESSION_DONE = 10
    const val LESSON_DONE = 15
    const val DRILL_CORRECT = 2
    const val WORD_REVIEWED = 1
    const val LISTENING_ANSWER = 2
    const val SHADOWING_SENTENCE = 2
    const val TEST_DONE = 20

    fun forSpeaking(seconds: Double): Int = (seconds / 60.0 * PER_SPEAKING_MINUTE).roundToInt()
}

data class StreakInfo(val current: Int, val best: Int, val freezes: Int, val goalMetToday: Boolean)

object Streak {
    private const val DAYS_PER_FREEZE = 7
    private const val MAX_FREEZES = 2

    /**
     * Counts consecutive days the daily goal was met. Every seven days earn a freeze, at most two
     * held at once, and a freeze automatically covers one missed day so one bad day doesn't erase
     * weeks of work. Today not being done yet doesn't break the streak.
     */
    fun compute(goalDays: Set<LocalDate>, today: LocalDate): StreakInfo {
        if (goalDays.isEmpty()) return StreakInfo(0, 0, 0, false)
        var current = 0
        var best = 0
        var freezes = 0
        var day = goalDays.min()
        while (day.isBefore(today)) {
            if (day in goalDays) {
                current++
                if (current % DAYS_PER_FREEZE == 0 && freezes < MAX_FREEZES) freezes++
            } else if (current > 0 && freezes > 0) {
                freezes--
            } else {
                current = 0
            }
            best = max(best, current)
            day = day.plusDays(1)
        }
        val metToday = today in goalDays
        if (metToday) {
            current++
            if (current % DAYS_PER_FREEZE == 0 && freezes < MAX_FREEZES) freezes++
        }
        return StreakInfo(current, max(best, current), freezes, metToday)
    }
}
