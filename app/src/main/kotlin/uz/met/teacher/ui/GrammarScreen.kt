package uz.met.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.met.core.AnswerCheck
import uz.met.core.ClaudeGateway
import uz.met.core.Drill
import uz.met.core.Effort
import uz.met.core.GrammarLesson
import uz.met.core.Prompts
import uz.met.core.Xp
import uz.met.core.matchesAny
import uz.met.core.wordsOf
import uz.met.teacher.AppGraph
import uz.met.teacher.voice.VoiceInputController

@Composable
fun GrammarScreen(graph: AppGraph, nav: Navigator, screen: Screen.Grammar) {
    val loader = rememberLoader {
        lessonContent(graph, screen.lessonId, GrammarLesson.serializer()) {
            val mistakes = if (screen.category != null) graph.repo.dao.recentErrors(screen.category) else graph.repo.dao.latestErrors()
            val model = graph.settings.tutorModel
            val level = graph.sessionLevel(screen.lessonId)
            graph.claude {
                it.structured(
                    "grammar", model, Effort.MEDIUM, Prompts.grammarSystem,
                    Prompts.grammarUser(screen.topic, level, mistakes.map { e -> "\"${e.said}\" should be \"${e.better}\"" }),
                    GrammarLesson.serializer(),
                )
            }
        }
    }
    var drillIndex by remember { mutableIntStateOf(-1) }
    var correct by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    ScreenFrame(title = loader.value?.titleUz ?: "Grammatika", onBack = nav::back) {
        ScrollArea {
            LoaderContent(loader, "Dars tayyorlanmoqda...") { lesson ->
                when {
                    drillIndex < 0 -> Explanation(graph, lesson) { drillIndex = 0 }
                    drillIndex < lesson.drills.size -> DrillStep(
                        graph,
                        lesson.drills[drillIndex],
                        position = "${drillIndex + 1} / ${lesson.drills.size}",
                        onResult = { ok -> if (ok) correct++ },
                        onNext = {
                            drillIndex++
                            if (drillIndex >= lesson.drills.size && screen.lessonId != null) {
                                scope.launch { graph.repo.markLessonDone(screen.lessonId) }
                            }
                        },
                    )
                    else -> {
                        MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Dars tugadi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${lesson.drills.size} ta mashqdan $correct tasi birinchi urinishda to'g'ri.")
                        }
                        Button(onClick = nav::back, modifier = Modifier.fillMaxWidth()) { Text("Davom etish") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Explanation(graph: AppGraph, lesson: GrammarLesson, onStart: () -> Unit) {
    MetCard { Text(lesson.explanationUz) }
    SectionTitle("Misollar")
    lesson.examples.forEach { example ->
        MetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(example.en, fontWeight = FontWeight.Medium)
                    Text(example.uz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ListenButton(graph, example.en)
            }
        }
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Og'zaki mashqni boshlash") }
}

/** Accepted answers are checked on the phone first, so most correct answers cost nothing. */
@Composable
fun DrillStep(graph: AppGraph, drill: Drill, position: String, onResult: (Boolean) -> Unit, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember(drill) { mutableStateOf<AnswerCheck?>(null) }
    var heard by remember(drill) { mutableStateOf<String?>(null) }
    var checking by remember(drill) { mutableStateOf(false) }
    var error by remember(drill) { mutableStateOf<String?>(null) }
    var attempts by remember(drill) { mutableIntStateOf(0) }

    fun evaluate(answer: String) {
        heard = answer
        error = null
        scope.launch {
            if (matchesAny(answer, drill.acceptedEn + drill.answerEn)) {
                result = AnswerCheck(true, "Juda yaxshi!", drill.answerEn)
            } else {
                checking = true
                try {
                    val model = graph.settings.tutorModel
                    result = graph.claude {
                        it.structured(
                            "check", model, Effort.LOW, Prompts.answerCheckSystem,
                            Prompts.answerCheckUser(drill.promptUz, drill.answerEn, answer), AnswerCheck.serializer(),
                        )
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    error = ClaudeGateway.describe(e)
                }
                checking = false
            }
            val checked = result ?: return@launch
            if (attempts == 0) onResult(checked.correct)
            attempts++
            if (checked.correct) graph.repo.addXp(Xp.DRILL_CORRECT)
        }
    }

    val voice = remember(drill) {
        VoiceInputController(graph, scope, onFailure = { error = it }) { captured ->
            scope.launch { graph.repo.addSpeaking(null, captured.speechSeconds, wordsOf(captured.text).size, captured.pauses) }
            evaluate(captured.text)
        }
    }

    Text(position, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Text("Inglizcha ayting:", style = MaterialTheme.typography.labelMedium)
        Text(drill.promptUz, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
    heard?.let { MetCard { Text("Siz: $it") } }
    error?.let { ErrorBanner(it) }
    val checked = result
    when {
        checking -> LoadingBlock("Tekshirilmoqda...")
        checked != null -> {
            MetCard(color = if (checked.correct) Leaf.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer) {
                Text(if (checked.correct) "To'g'ri!" else "Deyarli", fontWeight = FontWeight.Bold)
                Text(checked.feedbackUz)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(checked.corrected, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    ListenButton(graph, checked.corrected)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!checked.correct) {
                    OutlinedButton(onClick = { result = null; heard = null }, modifier = Modifier.weight(1f)) { Text("Yana urinish") }
                }
                Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Keyingisi") }
            }
        }
        else -> MicButton(voice)
    }
}
