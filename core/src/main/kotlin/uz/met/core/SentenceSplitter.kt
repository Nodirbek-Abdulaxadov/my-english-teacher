package uz.met.core

/**
 * Cuts streamed text into pieces the speech synthesizer can say on their own, so the first
 * sentence is spoken before the whole reply has arrived.
 */
class SentenceSplitter(private val softLimit: Int = 80) {
    private val buffer = StringBuilder()

    fun feed(delta: String): List<String> {
        buffer.append(delta)
        val pieces = mutableListOf<String>()
        while (true) {
            val end = nextBoundary()
            if (end < 0) break
            pieces += buffer.substring(0, end).trim()
            buffer.delete(0, end)
        }
        return pieces.filter { it.isNotEmpty() }
    }

    /** Returns whatever is left once the stream has ended. */
    fun flush(): String? {
        val rest = buffer.toString().trim()
        buffer.clear()
        return rest.ifEmpty { null }
    }

    private fun nextBoundary(): Int {
        // The last character is skipped: only the next one can show that a sentence really ended.
        for (i in 0 until buffer.length - 1) {
            if (buffer[i] in SENTENCE_END && buffer[i + 1].isWhitespace() && !endsWithAbbreviation(i)) return i + 1
        }
        if (buffer.length < softLimit) return -1
        // A long sentence is cut at a pause, so the listener isn't kept waiting for its end.
        for (i in buffer.length - 2 downTo 0) {
            if (buffer[i] in SOFT_END && buffer[i + 1].isWhitespace()) return i + 1
        }
        return -1
    }

    private fun endsWithAbbreviation(dotIndex: Int): Boolean {
        if (buffer[dotIndex] != '.') return false
        val start = buffer.lastIndexOf(" ", dotIndex) + 1
        return buffer.substring(start, dotIndex).lowercase() in ABBREVIATIONS
    }

    private companion object {
        val SENTENCE_END = setOf('.', '?', '!')
        val SOFT_END = setOf(',', ';', ':')
        val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "prof", "e.g", "i.e", "vs", "st")
    }
}
