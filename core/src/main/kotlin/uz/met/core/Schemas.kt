package uz.met.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val MetJson = Json { ignoreUnknownKeys = true }

/**
 * Builds the JSON schema for structured outputs from a class's own shape, so the two can't drift
 * apart. Handles what the classes below use: objects, lists, strings, whole numbers, booleans.
 */
@OptIn(ExperimentalSerializationApi::class)
fun schemaOf(descriptor: SerialDescriptor): JsonObject = when (descriptor.kind) {
    StructureKind.CLASS -> buildJsonObject {
        put("type", "object")
        put(
            "properties",
            JsonObject((0 until descriptor.elementsCount).associate { i -> descriptor.getElementName(i) to schemaOf(descriptor.getElementDescriptor(i)) }),
        )
        put("required", JsonArray((0 until descriptor.elementsCount).map { JsonPrimitive(descriptor.getElementName(it)) }))
        put("additionalProperties", false)
    }
    StructureKind.LIST -> buildJsonObject {
        put("type", "array")
        put("items", schemaOf(descriptor.getElementDescriptor(0)))
    }
    PrimitiveKind.STRING -> buildJsonObject { put("type", "string") }
    PrimitiveKind.INT, PrimitiveKind.LONG -> buildJsonObject { put("type", "integer") }
    PrimitiveKind.BOOLEAN -> buildJsonObject { put("type", "boolean") }
    else -> error("Schema uchun qo'llanmagan tur: ${descriptor.kind}")
}

@Serializable data class WordItem(val term: String, val meaningUz: String, val example: String)
@Serializable data class ErrorItem(val category: String, val said: String, val better: String, val explanationUz: String)
@Serializable data class Upgrade(val said: String, val b2: String)
@Serializable data class ProfileUpdate(val facts: List<String>, val interests: List<String>, val recurringErrors: List<String>)

@Serializable
data class SessionAnalysis(
    val summaryUz: String,
    val strengthsUz: List<String>,
    val errors: List<ErrorItem>,
    val newWords: List<WordItem>,
    val upgrades: List<Upgrade>,
    val b2PlusWords: Int,
    val cefrEstimate: String,
    val profile: ProfileUpdate,
)

@Serializable data class Example(val en: String, val uz: String)
@Serializable data class Drill(val promptUz: String, val answerEn: String, val acceptedEn: List<String>)

@Serializable
data class GrammarLesson(val titleUz: String, val explanationUz: String, val examples: List<Example>, val drills: List<Drill>)

@Serializable data class AnswerCheck(val correct: Boolean, val feedbackUz: String, val corrected: String)
@Serializable data class WordPack(val words: List<WordItem>)
@Serializable data class BetterVersion(val better: String, val noteUz: String)

@Serializable data class ListeningLine(val speaker: Int, val text: String)
@Serializable data class ReplyHints(val options: List<Example>)

/** Everything the learner goes through before a role-play. */
@Serializable
data class PrepPack(
    val phrases: List<WordItem>,
    val dialogue: List<ListeningLine>,
    val repeat: List<String>,
    val drills: List<Drill>,
)
@Serializable data class ListeningQuestion(val question: String, val modelAnswer: String)

@Serializable
data class ListeningLesson(
    val title: String,
    val lines: List<ListeningLine>,
    val questions: List<ListeningQuestion>,
    val shadowing: List<String>,
    val dictation: List<String>,
)

/** The same exercises for audio the learner brought, whose words are already known. */
@Serializable
data class ImportedAudioExercises(val title: String, val questions: List<ListeningQuestion>, val newWords: List<WordItem>)

@Serializable
data class SpeakingTestResult(
    val cefr: String,
    val fluency: Int,
    val grammar: Int,
    val vocabulary: Int,
    val coherence: Int,
    val feedbackUz: String,
    val nextStepsUz: List<String>,
)

/** What the tutor knows about the learner between sessions. */
@Serializable
data class LearnerProfile(
    val name: String = "",
    val level: String = "A2/B1",
    val facts: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val recurringErrors: List<String> = emptyList(),
) {
    fun merged(update: ProfileUpdate, newLevel: String? = null): LearnerProfile = copy(
        level = newLevel?.takeIf { it.isNotBlank() } ?: level,
        facts = (facts + update.facts).distinct().takeLast(MAX_ITEMS),
        interests = (interests + update.interests).distinct().takeLast(MAX_ITEMS),
        // The newest assessment replaces the old one, because errors the learner has outgrown should go.
        recurringErrors = update.recurringErrors.ifEmpty { recurringErrors }.distinct().take(MAX_ITEMS),
    )

    private companion object {
        const val MAX_ITEMS = 25
    }
}
