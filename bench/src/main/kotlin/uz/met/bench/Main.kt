package uz.met.bench

import uz.met.core.ClaudeGateway
import uz.met.core.LearnerProfile
import uz.met.core.Prompts
import uz.met.core.SentenceSplitter
import uz.met.core.TutorModel

/**
 * Compares the tutor model settings on a scripted learner conversation: how long until the first
 * word and the first full sentence, what a turn costs, and whether the cache works. It reads
 * ANTHROPIC_API_KEY from the environment and spends real, if small, money.
 */
private val LEARNER_TURNS = listOf(
    "Hello. My name is Aziz and I live in Tashkent since five years.",
    "I work in a bank. I go to work by metro every day but yesterday I go by taxi because I was late.",
    "My manager is very strict. He don't like when people come late, and I am afraid to speak with him.",
    "I want to learn English because maybe I will find job in other country. It is my big dream.",
    "I like Germany or Canada. My brother live in Canada and he say the life is good but very cold.",
    "How I can say when I want to ask more money from my boss? I don't know this word.",
    "Thank you. I think I need to prepare before I will talk with him. Can you explain me how to start?",
    "OK. I will try. On weekend I usually play football with my friends and after we eat plov together.",
)

private data class Row(val firstTokenMs: Long, val firstSentenceMs: Long, val totalMs: Long, val outputTokens: Long)

private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

fun main() {
    val apiKey = checkNotNull(System.getenv("ANTHROPIC_API_KEY")) { "ANTHROPIC_API_KEY is not set" }
    val gateway = ClaudeGateway(apiKey)
    val system = Prompts.conversationSystem(LearnerProfile(), null)
    println("model                    | turns | first word | first sentence | total | output tok | \$/turn | cache read (last turn)")
    val samples = StringBuilder()
    for (model in TutorModel.entries) {
        val conversation = gateway.conversation(model, system)
        val rows = mutableListOf<Row>()
        var cost = 0.0
        var lastCacheRead = 0L
        var totalInput = 0L
        for ((index, line) in LEARNER_TURNS.withIndex()) {
            val start = System.nanoTime()
            var firstToken = -1L
            var firstSentence = -1L
            val splitter = SentenceSplitter()
            val result = conversation.send(line) { delta ->
                val now = (System.nanoTime() - start) / 1_000_000
                if (firstToken < 0) firstToken = now
                if (firstSentence < 0 && splitter.feed(delta).isNotEmpty()) firstSentence = now
            }
            val total = (System.nanoTime() - start) / 1_000_000
            if (firstSentence < 0) firstSentence = total
            cost += result.costUsd
            lastCacheRead = result.usage.cacheReadTokens
            totalInput = result.usage.inputTokens + result.usage.cacheReadTokens + result.usage.cacheWriteTokens
            // The first turn pays for the connection and the cache write, so it isn't representative.
            if (index > 0) rows += Row(firstToken, firstSentence, total, result.usage.outputTokens)
            samples.append("[${model.label}] learner: $line\n[${model.label}] tutor: ${result.text}\n")
            if (result.refused) samples.append("[${model.label}] REFUSED\n")
            if (result.truncated) samples.append("[${model.label}] TRUNCATED\n")
        }
        println(
            "%-24s | %5d | %7d ms | %11d ms | %5d | %10d | %.5f | %d of %d".format(
                model.label,
                rows.size,
                median(rows.map { it.firstTokenMs }),
                median(rows.map { it.firstSentenceMs }),
                median(rows.map { it.totalMs }),
                median(rows.map { it.outputTokens }),
                cost / LEARNER_TURNS.size,
                lastCacheRead,
                totalInput,
            ),
        )
    }
    println()
    print(samples)
}
