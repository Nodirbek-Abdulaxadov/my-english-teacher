package uz.met.core

/**
 * Sentences with typical learner mistakes, read aloud to see whether the speech recognizer keeps
 * the mistake. If it quietly fixes them, the tutor never sees them.
 */
data class WhisperProbe(val spoken: String, val wrong: String, val fixed: String) {
    fun classify(transcript: String): ProbeOutcome {
        val heard = " ${normalizeWords(transcript)} "
        return when {
            " ${normalizeWords(wrong)} " in heard -> ProbeOutcome.KEPT
            " ${normalizeWords(fixed)} " in heard -> ProbeOutcome.CORRECTED
            else -> ProbeOutcome.MISHEARD
        }
    }
}

enum class ProbeOutcome { KEPT, CORRECTED, MISHEARD }

object WhisperProbes {
    val all = listOf(
        WhisperProbe("I go to school yesterday.", "i go to school yesterday", "i went to school yesterday"),
        WhisperProbe("She don't like coffee.", "she don't", "she doesn't"),
        WhisperProbe("I have two brother.", "two brother", "two brothers"),
        WhisperProbe("He is teacher in my city.", "he is teacher", "he is a teacher"),
        WhisperProbe("I am agree with you.", "i am agree", "i agree"),
        WhisperProbe("My sister, he works in a bank.", "he works", "she works"),
        WhisperProbe("I live in Tashkent since five years.", "since", "for"),
        WhisperProbe("We was at home last night.", "we was", "we were"),
        WhisperProbe("Yesterday I have seen my friend.", "i have seen", "i saw"),
        WhisperProbe("I want that you help me.", "want that you", "want you to"),
        WhisperProbe("She is more taller than me.", "more taller", "she is taller"),
        WhisperProbe("There is many people here.", "there is many", "there are many"),
        WhisperProbe("I didn't went to work.", "didn't went", "didn't go"),
        WhisperProbe("Can you explain me this word?", "explain me", "explain this word to me"),
        WhisperProbe("I am interesting in music.", "interesting in", "interested in"),
        WhisperProbe("He go to the gym every day.", "he go", "he goes"),
        WhisperProbe("I listen music every morning.", "listen music", "listen to music"),
        WhisperProbe("Where you live now?", "where you live", "where do you live"),
        WhisperProbe("I very like this city.", "i very like", "i really like"),
        WhisperProbe("How much people work here?", "how much people", "how many people"),
    )
}
