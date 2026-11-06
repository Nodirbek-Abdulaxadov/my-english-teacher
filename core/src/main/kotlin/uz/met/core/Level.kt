package uz.met.core

/** What the app changes according to the learner's CEFR level. */
object Level {
    private val ORDER = listOf("A1", "A2", "A2+", "B1", "B1+", "B2", "B2+", "C1", "C2")
    private const val DEFAULT = "A2"

    /** The levels a learner can choose by hand. */
    val choices = listOf("A2", "A2+", "B1", "B1+", "B2")

    /** The profile starts at "A2/B1", which counts as its lower half. Anything unknown counts as A2. */
    fun normalize(level: String): String =
        level.substringBefore('/').trim().uppercase().takeIf { it in ORDER } ?: DEFAULT

    fun rank(level: String): Int = ORDER.indexOf(normalize(level))

    fun higher(a: String, b: String): String = if (rank(a) >= rank(b)) normalize(a) else normalize(b)

    /** How the tutor should pitch its English. */
    fun guide(level: String): String = when {
        rank(level) <= rank("A2") ->
            "Speak very simply: sentences of at most ten words, only common everyday words, mostly present and simple past. " +
                "Ask easy questions, often with two choices, such as \"Do you live in a city or a village?\". " +
                "Avoid idioms and phrasal verbs."
        rank(level) == rank("A2+") ->
            "Speak simply: short sentences and common words. Use a new word only when its meaning is clear from the context, " +
                "and ask questions that are easy to answer in a sentence or two."
        rank(level) <= rank("B1+") ->
            "Use natural everyday English with contractions, and now and then one useful B2 word or phrase that fits the moment."
        else ->
            "Speak naturally, as you would with a confident speaker. Use idioms and phrasal verbs, and ask them to explain, compare and justify."
    }

    /** Speech slows down a little for lower levels, on top of the speed in the settings. */
    fun speechPace(level: String): Float = when {
        rank(level) <= rank("A2") -> 0.9f
        rank(level) == rank("A2+") -> 0.95f
        else -> 1f
    }

    /** Seconds for the three rounds of the fluency exercise. The classic 4-3-2 is for stronger speakers. */
    fun fluencyRounds(level: String): List<Int> = when {
        rank(level) <= rank("A2+") -> listOf(90, 60, 45)
        rank(level) <= rank("B1+") -> listOf(180, 120, 90)
        else -> listOf(240, 180, 120)
    }
}
