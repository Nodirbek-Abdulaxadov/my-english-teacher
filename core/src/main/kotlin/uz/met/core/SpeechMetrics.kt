package uz.met.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val NON_WORD = Regex("[^a-z' ]+")
private val SPACES = Regex("\\s+")

/** Lowercase words only, so spoken answers can be compared with expected ones. */
fun normalizeWords(text: String): String =
    text.lowercase().replace('’', '\'').replace(NON_WORD, " ").replace(SPACES, " ").trim()

fun wordsOf(text: String): List<String> = normalizeWords(text).split(' ').filter { it.isNotEmpty() }

/** One word of the expected text and whether the learner's version contained it, in order. */
data class WordMatch(val word: String, val matched: Boolean)

/** Compares by longest common subsequence, keeping the original spelling of [expected] for display. */
fun compareWords(expected: String, heard: String): List<WordMatch> {
    val shown = expected.trim().split(SPACES).filter { it.isNotEmpty() }
    val want = shown.map { normalizeWords(it) }
    val got = wordsOf(heard)
    val table = Array(want.size + 1) { IntArray(got.size + 1) }
    for (i in want.indices.reversed()) {
        for (j in got.indices.reversed()) {
            table[i][j] = if (want[i] == got[j]) table[i + 1][j + 1] + 1 else max(table[i + 1][j], table[i][j + 1])
        }
    }
    val result = ArrayList<WordMatch>(shown.size)
    var i = 0
    var j = 0
    while (i < want.size) {
        if (j < got.size && want[i] == got[j]) {
            result += WordMatch(shown[i], true)
            i++
            j++
        } else if (j < got.size && table[i][j + 1] >= table[i + 1][j]) {
            j++
        } else {
            result += WordMatch(shown[i], false)
            i++
        }
    }
    return result
}

fun matchScore(matches: List<WordMatch>): Int =
    if (matches.isEmpty()) 0 else matches.count { it.matched } * 100 / matches.size

/** True if the spoken text contains the expected phrase as whole words. */
fun containsPhrase(heard: String, phrase: String): Boolean {
    val wanted = normalizeWords(phrase)
    return wanted.isNotEmpty() && " $wanted " in " ${normalizeWords(heard)} "
}

/** True if a spoken answer matches any accepted answer, ignoring case and punctuation. */
fun matchesAny(heard: String, accepted: List<String>): Boolean {
    val said = normalizeWords(heard)
    return accepted.any { normalizeWords(it) == said }
}

data class AudioStats(val speechSeconds: Double, val pauses: Int)

object AudioAnalysis {
    private const val FRAME_MS = 20
    private const val MIN_PAUSE_MS = 500
    private const val MIN_SILENCE_FOR_CUT_MS = 200

    private fun frameEnergies(samples: FloatArray, sampleRate: Int): FloatArray {
        val frame = sampleRate * FRAME_MS / 1000
        if (frame == 0) return FloatArray(0)
        return FloatArray(samples.size / frame) { index ->
            var sum = 0.0
            for (i in 0 until frame) {
                val s = samples[index * frame + i].toDouble()
                sum += s * s
            }
            sqrt(sum / frame).toFloat()
        }
    }

    /** A loudness level below which a frame counts as silence, scaled to how loud this recording is. */
    private fun silenceLevel(energies: FloatArray): Float {
        if (energies.isEmpty()) return 0f
        val sorted = energies.sorted()
        return max(0.005f, sorted[(sorted.size * 95) / 100] * 0.12f)
    }

    /** Speaking time and the pauses within it. Leading and trailing silence don't count. */
    fun stats(samples: FloatArray, sampleRate: Int): AudioStats {
        val energies = frameEnergies(samples, sampleRate)
        val level = silenceLevel(energies)
        val first = energies.indexOfFirst { it > level }
        if (first < 0) return AudioStats(0.0, 0)
        val last = energies.indexOfLast { it > level }
        var pauses = 0
        var voiced = 0
        var silentRun = 0
        for (i in first..last) {
            if (energies[i] > level) {
                if (silentRun * FRAME_MS >= MIN_PAUSE_MS) pauses++
                silentRun = 0
                voiced++
            } else {
                silentRun++
            }
        }
        return AudioStats(voiced * FRAME_MS / 1000.0, pauses)
    }

    /**
     * Cuts audio into pieces no longer than [maxSeconds], because Whisper handles at most about
     * 30 seconds at a time. Each cut falls in the quietest stretch near the limit, not mid-word.
     */
    fun splitForWhisper(samples: FloatArray, sampleRate: Int, maxSeconds: Int = 25): List<IntRange> {
        val maxLength = maxSeconds * sampleRate
        if (samples.size <= maxLength) return if (samples.isEmpty()) emptyList() else listOf(samples.indices)
        val frame = sampleRate * FRAME_MS / 1000
        val energies = frameEnergies(samples, sampleRate)
        val searchFrames = 5_000 / FRAME_MS
        val cutFrames = MIN_SILENCE_FOR_CUT_MS / FRAME_MS
        val pieces = mutableListOf<IntRange>()
        var start = 0
        while (samples.size - start > maxLength) {
            val limitFrame = (start + maxLength) / frame
            val from = max(start / frame + 1, limitFrame - searchFrames)
            var bestFrame = limitFrame
            var bestEnergy = Float.MAX_VALUE
            for (f in from..min(limitFrame, energies.size - cutFrames)) {
                var sum = 0f
                for (k in 0 until cutFrames) sum += energies[f + k]
                if (sum < bestEnergy) {
                    bestEnergy = sum
                    bestFrame = f
                }
            }
            val end = bestFrame * frame
            pieces += start until end
            start = end
        }
        pieces += start until samples.size
        return pieces
    }
}
