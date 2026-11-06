package uz.met.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import uz.met.core.AnswerCheck
import uz.met.core.ClaudeGateway
import uz.met.core.Effort
import uz.met.core.MetJson
import uz.met.core.Prompts
import uz.met.core.WordMatch
import uz.met.core.wordsOf
import uz.met.teacher.AppGraph
import uz.met.teacher.voice.Captured
import uz.met.teacher.voice.VoiceInputController

/** Loads a lesson's saved content, or generates and saves it the first time. */
suspend fun <T> lessonContent(graph: AppGraph, lessonId: String?, serializer: KSerializer<T>, generate: suspend () -> T): T {
    if (lessonId != null) {
        graph.repo.dao.lesson(lessonId)?.contentJson?.let { json ->
            runCatching { MetJson.decodeFromString(serializer, json) }.getOrNull()?.let { return it }
        }
    }
    val fresh = generate()
    if (lessonId != null) graph.repo.saveLessonContent(lessonId, MetJson.encodeToString(serializer, fresh))
    return fresh
}

/** Loads something from Claude with loading and retry state, for the screens that start that way. */
class Loader<T> {
    var value by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null)
    var attempt by mutableIntStateOf(0)
}

@Composable
fun <T> rememberLoader(load: suspend () -> T): Loader<T> {
    val loader = remember { Loader<T>() }
    LaunchedEffect(loader.attempt) {
        loader.error = null
        try {
            loader.value = load()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loader.error = ClaudeGateway.describe(e)
        }
    }
    return loader
}

@Composable
fun <T> LoaderContent(loader: Loader<T>, loadingText: String, content: @Composable (T) -> Unit) {
    val value = loader.value
    val error = loader.error
    when {
        value != null -> content(value)
        error != null -> ErrorBanner(error) { loader.attempt++ }
        else -> LoadingBlock(loadingText)
    }
}

/** Expected words in green or red, the coarse view of what the speech recognizer understood. */
@Composable
fun WordMatchText(matches: List<WordMatch>) {
    val miss = MaterialTheme.colorScheme.error
    Text(
        buildAnnotatedString {
            matches.forEachIndexed { index, match ->
                if (index > 0) append(' ')
                withStyle(SpanStyle(color = if (match.matched) Leaf else miss, fontWeight = if (match.matched) FontWeight.Normal else FontWeight.Bold)) {
                    append(match.word)
                }
            }
        },
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * A question answered aloud. Claude checks the answer, the feedback appears, and [onNext] moves on.
 * Typing is offered too, for when speaking isn't possible.
 */
@Composable
fun SpokenQuestion(
    graph: AppGraph,
    task: String,
    modelAnswer: String,
    heading: @Composable () -> Unit,
    xpIfCorrect: Int,
    nextLabel: String,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var heard by remember(task) { mutableStateOf<String?>(null) }
    var check by remember(task) { mutableStateOf<AnswerCheck?>(null) }
    var checking by remember(task) { mutableStateOf(false) }
    var error by remember(task) { mutableStateOf<String?>(null) }
    var typed by remember(task) { mutableStateOf("") }

    fun evaluate(answer: String) {
        heard = answer
        checking = true
        error = null
        scope.launch {
            try {
                val model = graph.settings.tutorModel
                val result = graph.claude {
                    it.structured(
                        "check", model, Effort.LOW, Prompts.answerCheckSystem,
                        Prompts.answerCheckUser(task, modelAnswer, answer), AnswerCheck.serializer(),
                    )
                }
                check = result
                if (result.correct) graph.repo.addXp(xpIfCorrect)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = ClaudeGateway.describe(e)
            }
            checking = false
        }
    }

    val voice = remember(task) {
        VoiceInputController(graph, scope, onFailure = { error = it }) { captured: Captured ->
            scope.launch { graph.repo.addSpeaking(null, captured.speechSeconds, wordsOf(captured.text).size, captured.pauses) }
            evaluate(captured.text)
        }
    }

    heading()
    heard?.let { MetCard { Text("Siz: $it") } }
    error?.let { ErrorBanner(it, onRetry = heard?.let { h -> { evaluate(h) } }) }
    val result = check
    when {
        checking -> LoadingBlock("Tekshirilmoqda...")
        result != null -> {
            MetCard(color = if (result.correct) Leaf.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer) {
                Text(if (result.correct) "To'g'ri!" else "Deyarli", fontWeight = FontWeight.Bold)
                Text(result.feedbackUz)
                if (!result.correct) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(result.corrected, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        ListenButton(graph, result.corrected)
                    }
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(nextLabel) }
        }
        else -> {
            MicButton(voice)
            if (voice.state == VoiceInputController.State.IDLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), placeholder = { Text("Yoki yozib javob bering") }, singleLine = true)
                    TextButton(onClick = { evaluate(typed.trim()) }, enabled = typed.isNotBlank()) { Text("Yuborish") }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
