package uz.met.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** How a recall went. The values match the FSRS grades. */
enum class Rating(val grade: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

data class CardMemory(
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewMs: Long = 0,
    val dueMs: Long = 0,
)

/** FSRS-4.5 spaced repetition with its published default weights. */
object Fsrs {
    private val W = doubleArrayOf(
        0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031, 1.6474,
        0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val TARGET_RETENTION = 0.9
    private const val MAX_INTERVAL_DAYS = 365L
    private const val DAY_MS = 86_400_000L
    private const val RELEARN_MS = 10 * 60_000L

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    fun intervalDays(stability: Double): Long {
        val days = stability / FACTOR * (TARGET_RETENTION.pow(1 / DECAY) - 1)
        return days.roundToLong().coerceIn(1, MAX_INTERVAL_DAYS)
    }

    fun review(card: CardMemory, rating: Rating, nowMs: Long): CardMemory {
        val grade = rating.grade
        val isNew = card.reps == 0
        val stability: Double
        val difficulty: Double
        if (isNew) {
            stability = W[grade - 1]
            difficulty = initialDifficulty(grade)
        } else {
            val elapsedDays = max(0.0, (nowMs - card.lastReviewMs).toDouble() / DAY_MS)
            val recall = retrievability(elapsedDays, card.stability)
            difficulty = nextDifficulty(card.difficulty, grade)
            stability = if (rating == Rating.AGAIN) {
                min(card.stability, forgetStability(card.difficulty, card.stability, recall))
            } else {
                recallStability(card.difficulty, card.stability, recall, grade)
            }
        }
        val due = if (rating == Rating.AGAIN) nowMs + RELEARN_MS else nowMs + intervalDays(stability) * DAY_MS
        return CardMemory(
            stability = stability,
            difficulty = difficulty,
            reps = card.reps + 1,
            lapses = card.lapses + if (rating == Rating.AGAIN && !isNew) 1 else 0,
            lastReviewMs = nowMs,
            dueMs = due,
        )
    }

    private fun initialDifficulty(grade: Int) = (W[4] - (grade - 3) * W[5]).coerceIn(1.0, 10.0)

    private fun nextDifficulty(difficulty: Double, grade: Int): Double {
        val moved = difficulty - W[6] * (grade - 3)
        return (W[7] * initialDifficulty(3) + (1 - W[7]) * moved).coerceIn(1.0, 10.0)
    }

    private fun recallStability(d: Double, s: Double, r: Double, grade: Int): Double {
        val hardPenalty = if (grade == Rating.HARD.grade) W[15] else 1.0
        val easyBonus = if (grade == Rating.EASY.grade) W[16] else 1.0
        return s * (exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp(W[10] * (1 - r)) - 1) * hardPenalty * easyBonus + 1)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double =
        W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r))
}
