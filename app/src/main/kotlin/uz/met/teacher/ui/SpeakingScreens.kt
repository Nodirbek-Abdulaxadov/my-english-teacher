package uz.met.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.met.core.ClaudeGateway
import uz.met.core.Effort
import uz.met.core.LearningPath
import uz.met.core.Level
import uz.met.core.Prompts
import uz.met.core.SpeakingTestResult
import uz.met.core.SpeakingTests
import uz.met.core.wordsOf
import uz.met.teacher.AppGraph
import uz.met.teacher.data.SessionEntity
import uz.met.teacher.data.TurnEntity
import uz.met.teacher.voice.Captured
import uz.met.teacher.voice.VoiceInputController

private data class FluencyRound(val text: String, val wordsPerMinute: Int, val pauses: Int, val seconds: Double)

/** The 4-3-2 idea, scaled to the level: the same talk three times, each shorter, which trains speed without new content. */
@Composable
fun FluencyScreen(graph: AppGraph, nav: Navigator, screen: Screen.Fluency) {
    KeepScreenOn()
    val scope = rememberCoroutineScope()
    val limits = remember {
        Level.fluencyRounds(screen.lessonId?.let { LearningPath.unitOf(it)?.level } ?: graph.settings.profile.level)
    }
    val rounds = remember { mutableStateListOf<FluencyRound>() }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val round = rounds.size

    val voice = remember(round) {
        VoiceInputController(graph, scope, maxSeconds = limits.getOrElse(round) { 120 }, onFailure = { error = it }) { captured: Captured ->
            val minutes = (captured.speechSeconds / 60).coerceAtLeast(0.01)
            rounds += FluencyRound(captured.text, (wordsOf(captured.text).size / minutes).toInt(), captured.pauses, captured.speechSeconds)
        }
    }

    fun finish() {
        saving = true
        scope.launch {
            val now = System.currentTimeMillis()
            val id = graph.repo.dao.insertSession(
                SessionEntity(kind = "FLUENCY", title = "Ravonlik mashqi", lessonId = screen.lessonId, startedAt = now),
            )
            rounds.forEachIndexed { index, item ->
                graph.repo.dao.insertTurn(TurnEntity(sessionId = id, fromLearner = true, text = item.text, at = now + index))
                graph.repo.addSpeaking(id, item.seconds, wordsOf(item.text).size, item.pauses)
            }
            screen.lessonId?.let { graph.repo.markLessonDone(it) }
            nav.replace(Screen.Summary(id))
        }
    }

    ScreenFrame(title = "Ravonlik mashqi", onBack = nav::back) {
        ScrollArea {
            MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("Mavzu", style = MaterialTheme.typography.labelMedium)
                Text(screen.topic, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("Bitta hikoyani uch marta aytasiz: ${limits.joinToString(", ") { formatSeconds(it) }} daqiqada. Har safar gapingiz tezroq va ravonroq bo'ladi. Vaqtingiz tugasa, yozuv o'zi to'xtaydi. Xohlasangiz, oldinroq ham tugatishingiz mumkin.")
            rounds.forEachIndexed { index, item ->
                MetCard {
                    Text("${index + 1}-urinish: ${item.wordsPerMinute} so'z/daqiqa, ${item.pauses} ta pauza", fontWeight = FontWeight.Medium)
                }
            }
            error?.let { ErrorBanner(it) }
            when {
                saving -> LoadingBlock("Saqlanmoqda...")
                round < limits.size -> MicButton(voice, idleLabel = "${round + 1}-urinish, ${formatSeconds(limits[round])}. Boshlash uchun bosing")
                else -> Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("Tahlilni ko'rish") }
            }
        }
    }
}

/** Three spoken answers assessed together, for the placement test and the monthly check. */
@Composable
fun SpeakingTestScreen(graph: AppGraph, nav: Navigator, testNumber: Int) {
    KeepScreenOn()
    val scope = rememberCoroutineScope()
    val tasks = remember { SpeakingTests.tasksFor(testNumber) }
    val answers = remember { mutableStateListOf<Captured>() }
    var result by remember { mutableStateOf<SpeakingTestResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val index = answers.size

    fun assess() {
        checking = true
        error = null
        scope.launch {
            try {
                val seconds = answers.sumOf { it.speechSeconds }.coerceAtLeast(1.0)
                val words = answers.sumOf { wordsOf(it.text).size }
                val perMinute = (words / (seconds / 60)).toInt()
                val pauses = answers.sumOf { it.pauses }
                val model = graph.settings.tutorModel
                val assessed = graph.claude {
                    it.structured(
                        "test", model, Effort.HIGH, Prompts.speakingTestSystem,
                        Prompts.speakingTestUser(tasks.map { t -> t.promptEn }.zip(answers.map { a -> a.text }), perMinute, pauses),
                        SpeakingTestResult.serializer(),
                    )
                }
                graph.repo.saveTest(assessed, perMinute, pauses)
                result = assessed
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = ClaudeGateway.describe(e)
            }
            checking = false
        }
    }

    val voice = remember(index) {
        VoiceInputController(graph, scope, maxSeconds = 120, onFailure = { error = it }) { captured ->
            answers += captured
            scope.launch { graph.repo.addSpeaking(null, captured.speechSeconds, wordsOf(captured.text).size, captured.pauses) }
            if (answers.size == tasks.size) assess()
        }
    }

    ScreenFrame(title = "Og'zaki test", onBack = nav::back) {
        ScrollArea {
            val assessed = result
            when {
                assessed != null -> {
                    MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("Taxminiy daraja: ${assessed.cefr}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Bu baho rasmiy emas.", style = MaterialTheme.typography.bodySmall)
                    }
                    MetCard {
                        Text("Ravonlik: ${assessed.fluency}/5")
                        Text("Grammatika: ${assessed.grammar}/5")
                        Text("So'z boyligi: ${assessed.vocabulary}/5")
                        Text("Izchillik: ${assessed.coherence}/5")
                    }
                    MetCard { Text(assessed.feedbackUz) }
                    SectionTitle("Keyingi qadamlar")
                    MetCard { assessed.nextStepsUz.forEach { Text("• $it") } }
                    if (testNumber == 0) {
                        val unit = LearningPath.units.indexOfFirst { it.id == graph.settings.pathStartUnit } + 1
                        MetCard { Text("Yo'lni $unit-bo'limdan boshlaysiz. Qiyin yoki oson tuyulsa, Sozlamada darajani o'zgartirishingiz mumkin.") }
                    }
                    Button(onClick = nav::back, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text("Davom etish") }
                }
                checking -> LoadingBlock("Javoblaringiz baholanmoqda...")
                index >= tasks.size -> {
                    error?.let { ErrorBanner(it) { assess() } }
                }
                else -> {
                    Text("Savol ${index + 1} / ${tasks.size}", style = MaterialTheme.typography.labelLarge)
                    MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(tasks[index].promptUz, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(tasks[index].promptEn, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Inglizcha javob bering, 1-2 daqiqa gapiring. Xato qilishdan qo'rqmang.")
                    error?.let { ErrorBanner(it) }
                    MicButton(voice)
                }
            }
        }
    }
}
