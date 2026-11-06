package uz.met.core

/** Prices in USD per million tokens. */
data class Price(val input: Double, val output: Double, val cacheRead: Double) {
    /** Writes to the default 5-minute cache cost 1.25x the input price. */
    val cacheWrite: Double get() = input * 1.25
}

/** The model settings a learner can choose from. Prices were checked on 2026-10-06. */
enum class TutorModel(
    val label: String,
    val noteUz: String,
    val id: String,
    /** Only Sonnet 5.5 can run with thinking off. Opus 5.5 can't, so it is always on there. */
    val thinkingOff: Boolean,
    /** Haiku has no server-side fallback, so the parameter is not sent for it. */
    val serverFallback: Boolean,
    val price: Price,
) {
    OPUS("Opus 5.5", "Eng kuchli, kuniga 1 soatga ~$40/oy", "claude-opus-5-5", false, true, Price(4.0, 20.0, 0.20)),
    SONNET("Sonnet 5.5", "Kuchli va arzonroq, ~$23/oy", "claude-sonnet-5-5", false, true, Price(2.0, 10.0, 0.20)),
    SONNET_FAST("Sonnet 5.5 tezkor", "Thinking o'chiq, javob eng tez", "claude-sonnet-5-5", true, true, Price(2.0, 10.0, 0.20)),
    HAIKU("Haiku 5.5", "Eng arzon, ~$1/oy", "claude-haiku-5-5", false, false, Price(0.10, 0.50, 0.01)),
}

data class TurnUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
) {
    fun costUsd(price: Price): Double =
        (inputTokens * price.input +
            outputTokens * price.output +
            cacheReadTokens * price.cacheRead +
            cacheWriteTokens * price.cacheWrite) / 1_000_000.0
}

/** One billed request, for the spending counter. */
data class UsageRecord(val kind: String, val model: TutorModel, val usage: TurnUsage, val costUsd: Double)
