package uz.met.core

import com.anthropic.client.AnthropicClient
import com.anthropic.helpers.BetaMessageAccumulator
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaFallbacksParam
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaThinkingConfigBetweenTools
import com.anthropic.models.beta.messages.BetaUserMessageParam
import com.anthropic.models.beta.messages.MessageCreateParams

data class TurnResult(
    val text: String,
    val refused: Boolean,
    val truncated: Boolean,
    val usage: TurnUsage,
    val costUsd: Double,
)

/**
 * One spoken conversation. History is append-only: the full assistant content, thinking blocks
 * included, goes back on every request, which is what keeps both the prompt cache and preserved
 * thinking valid. Calls block, so run them off the main thread, and one at a time.
 */
class TutorConversation(
    private val client: AnthropicClient,
    private val model: TutorModel,
    private val systemPrompt: String,
    private val onUsage: (UsageRecord) -> Unit = {},
) {
    private val history = mutableListOf<BetaMessageParam>()

    /** Streams the reply to [onText]. A turn that fails or is refused leaves no trace in history. */
    fun send(userText: String, onText: (String) -> Unit): TurnResult {
        history += BetaMessageParam.ofUser(BetaUserMessageParam.builder().content(userText).build())
        try {
            val message = client.streamToMessage(buildParams(), onText)
            val stopReason = message.stopReason().orElse(null)
            val refused = stopReason == BetaStopReason.REFUSAL
            if (refused) dropLastTurn() else history += message.toParam()

            val usage = message.turnUsage()
            val cost = usage.costUsd(model.price)
            onUsage(UsageRecord("conversation", model, usage, cost))
            return TurnResult(
                text = message.plainText(),
                refused = refused,
                truncated = stopReason == BetaStopReason.MAX_TOKENS,
                usage = usage,
                costUsd = cost,
            )
        } catch (e: Exception) {
            dropLastTurn()
            throw e
        }
    }

    private fun dropLastTurn() {
        history.removeAt(history.lastIndex)
    }

    private fun buildParams(): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(model.id)
            // Replies are deliberately short, but thinking counts against this limit too.
            .maxTokens(2048L)
            .system(systemPrompt)
            .messages(history.toList())
            .cacheControl(BetaCacheControlEphemeral.builder().build())
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
        if (model.thinkingOff) builder.thinking(BetaThinkingConfigBetweenTools.builder().build())
        builder.withServerFallback(model)
        return builder.build()
    }
}

internal fun MessageCreateParams.Builder.withServerFallback(model: TutorModel) {
    if (model.serverFallback) {
        addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
        fallbacks(BetaFallbacksParam.ofDefault())
    }
}

internal fun AnthropicClient.streamToMessage(params: MessageCreateParams, onText: (String) -> Unit): BetaMessage {
    val accumulator = BetaMessageAccumulator.create()
    beta().messages().createStreaming(params).use { stream ->
        stream.stream().forEach { event ->
            accumulator.accumulate(event)
            event.contentBlockDelta()
                .flatMap { it.delta().text() }
                .ifPresent { onText(it.text()) }
        }
    }
    return accumulator.message()
}

internal fun BetaMessage.plainText(): String =
    content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")

internal fun BetaMessage.turnUsage(): TurnUsage = usage().let {
    TurnUsage(
        inputTokens = it.inputTokens(),
        outputTokens = it.outputTokens(),
        cacheReadTokens = it.cacheReadInputTokens().orElse(0L),
        cacheWriteTokens = it.cacheCreationInputTokens().orElse(0L),
    )
}
