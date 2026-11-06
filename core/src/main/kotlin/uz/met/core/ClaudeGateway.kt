package uz.met.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

enum class Effort(val api: BetaOutputConfig.Effort) {
    LOW(BetaOutputConfig.Effort.LOW),
    MEDIUM(BetaOutputConfig.Effort.MEDIUM),
    HIGH(BetaOutputConfig.Effort.HIGH),
}

/** A Claude failure explained in words the learner can act on. */
class ClaudeFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Every Claude request the app makes, apart from the conversation's own turns, goes through here.
 * Calls block, so run them off the main thread.
 */
class ClaudeGateway(
    apiKey: String,
    baseUrl: String? = null,
    private val onUsage: (UsageRecord) -> Unit = {},
) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .build()

    /** Checks the key with a free request. Throws if the API rejects it. */
    fun verifyKey() {
        client.models().list()
    }

    fun conversation(model: TutorModel, systemPrompt: String): TutorConversation =
        TutorConversation(client, model, systemPrompt, onUsage)

    /** Asks for JSON matching [serializer]'s shape, enforced on the API side by structured outputs. */
    fun <T> structured(
        kind: String,
        model: TutorModel,
        effort: Effort,
        system: String,
        user: String,
        serializer: KSerializer<T>,
    ): T {
        val schema = BetaJsonOutputFormat.Schema.builder()
            .putAllAdditionalProperties(schemaOf(serializer.descriptor).mapValues { it.value.toJsonValue() })
            .build()
        val builder = MessageCreateParams.builder()
            .model(model.id)
            // Generous, because thinking counts against this limit and a cut-off JSON is useless.
            .maxTokens(8000L)
            .system(system)
            .addUserMessage(user)
            .outputConfig(
                BetaOutputConfig.builder()
                    .effort(effort.api)
                    .format(BetaJsonOutputFormat.builder().schema(schema).build())
                    .build(),
            )
        builder.withServerFallback(model)

        val message = client.streamToMessage(builder.build()) {}
        val usage = message.turnUsage()
        onUsage(UsageRecord(kind, model, usage, usage.costUsd(model.price)))
        when (message.stopReason().orElse(null)) {
            BetaStopReason.REFUSAL -> throw ClaudeFailure("Claude bu so'rovga javob bermadi. Qayta urinib ko'ring.")
            BetaStopReason.MAX_TOKENS -> throw ClaudeFailure("Javob juda uzun chiqib, kesilib qoldi. Qayta urinib ko'ring.")
            else -> Unit
        }
        return MetJson.decodeFromString(serializer, message.plainText())
    }

    companion object {
        /** Turns any error from a Claude call into a sentence for the learner. */
        fun describe(error: Throwable): String = when (error) {
            is ClaudeFailure -> error.message.orEmpty()
            is UnauthorizedException -> "API kalit noto'g'ri. Sozlamada kalitni tekshiring."
            is PermissionDeniedException -> "Bu API kalitga ruxsat yo'q. Anthropic Console'ni tekshiring."
            is RateLimitException -> "So'rovlar juda ko'p bo'ldi. Bir daqiqadan keyin urinib ko'ring."
            is BadRequestException ->
                if (error.message.orEmpty().contains("credit", ignoreCase = true)) {
                    "Anthropic hisobingizda kredit qolmagan."
                } else {
                    "So'rov rad etildi: ${error.message}"
                }
            is AnthropicServiceException -> "Claude xizmatida xato (${error.statusCode()}). Birozdan keyin urinib ko'ring."
            is AnthropicIoException -> "Internet bilan aloqa yo'q. Tarmoqni tekshiring."
            else -> error.message ?: "Noma'lum xato"
        }
    }
}

private fun JsonElement.toJsonValue(): JsonValue = JsonValue.from(toPlain())

private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: content
}
