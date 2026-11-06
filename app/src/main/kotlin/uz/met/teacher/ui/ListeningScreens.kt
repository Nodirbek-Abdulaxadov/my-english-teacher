package uz.met.teacher.ui

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.met.core.ClaudeGateway
import uz.met.core.Effort
import uz.met.core.ImportedAudioExercises
import uz.met.core.ListeningLesson
import uz.met.core.ListeningLine
import uz.met.core.Prompts
import uz.met.core.WordMatch
import uz.met.core.Xp
import uz.met.core.compareWords
import uz.met.core.matchScore
import uz.met.core.wordsOf
import uz.met.teacher.AppGraph
import uz.met.teacher.speech.AudioDecoder
import uz.met.teacher.speech.Utterance
import uz.met.teacher.voice.VoiceInputController

private enum class ListenStep { LISTEN, QUESTIONS, RETELL, SHADOW, DICTATION, DONE }

/** A second voice for dialogues, as far from the learner's chosen voice as possible. */
private fun partnerVoice(graph: AppGraph): Int {
    val count = graph.settings.tts.voiceCount
    return (graph.settings.voice + count / 2) % count
}

@Composable
fun ListeningScreen(graph: AppGraph, nav: Navigator, screen: Screen.Listening) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader {
        lessonContent(graph, screen.lessonId, ListeningLesson.serializer()) {
            val model = graph.settings.tutorModel
            val level = graph.sessionLevel(screen.lessonId)
            graph.claude {
                it.structured(
                    "listening", model, Effort.MEDIUM, Prompts.listeningSystem,
                    Prompts.listeningUser(screen.theme, level), ListeningLesson.serializer(),
                )
            }
        }
    }
    var step by remember { mutableStateOf(ListenStep.LISTEN) }
    var index by remember { mutableIntStateOf(0) }
    fun next(to: ListenStep) {
        step = to
        index = 0
    }

    ScreenFrame(title = loader.value?.title ?: "Tinglash", onBack = nav::back) {
        ScrollArea {
            LoaderContent(loader, "Dialog yozilmoqda...") { lesson ->
                when (step) {
                    ListenStep.LISTEN -> DialoguePlayer(
                        graph, lesson.lines,
                        intro = "Avval matnsiz tinglang. Nima haqida ekanini tushunishga harakat qiling.",
                        nextLabel = "Savollarga o'tish",
                    ) { next(ListenStep.QUESTIONS) }
                    ListenStep.QUESTIONS -> {
                        val question = lesson.questions.getOrNull(index)
                        if (question == null) {
                            LaunchedEffect(Unit) { next(ListenStep.RETELL) }
                        } else {
                            SpokenQuestion(
                                graph, question.question, question.modelAnswer,
                                heading = {
                                    Text("Savol ${index + 1} / ${lesson.questions.size}", style = MaterialTheme.typography.labelLarge)
                                    MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(question.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            ListenButton(graph, question.question)
                                        }
                                    }
                                },
                                xpIfCorrect = Xp.LISTENING_ANSWER,
                                nextLabel = "Keyingisi",
                                onNext = { index++ },
                            )
                        }
                    }
                    ListenStep.RETELL -> {
                        val script = lesson.lines.joinToString(" ") { it.text }
                        SpokenQuestion(
                            graph, "Retell this dialogue in your own words: $script", script,
                            heading = {
                                MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("Qayta aytib bering", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Eshitganingizni o'z so'zlaringiz bilan inglizcha aytib bering.")
                                }
                            },
                            xpIfCorrect = Xp.LESSON_DONE,
                            nextLabel = "Keyingisi",
                            onNext = { next(ListenStep.SHADOW) },
                        )
                    }
                    ListenStep.SHADOW -> {
                        val sentence = lesson.shadowing.getOrNull(index)
                        if (sentence == null) {
                            LaunchedEffect(Unit) { next(ListenStep.DICTATION) }
                        } else {
                            ShadowingStep(graph, sentence, "${index + 1} / ${lesson.shadowing.size}") { index++ }
                        }
                    }
                    ListenStep.DICTATION -> {
                        val sentence = lesson.dictation.getOrNull(index)
                        if (sentence == null) {
                            LaunchedEffect(Unit) { next(ListenStep.DONE) }
                        } else {
                            DictationStep(graph, sentence, "${index + 1} / ${lesson.dictation.size}") { index++ }
                        }
                    }
                    ListenStep.DONE -> {
                        LaunchedEffect(Unit) { screen.lessonId?.let { graph.repo.markLessonDone(it) } }
                        MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Dars tugadi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = { scope.launch { nav.back() } }, modifier = Modifier.fillMaxWidth()) { Text("Davom etish") }
                    }
                }
            }
        }
    }
}

/** Plays a two-voice dialogue at a chosen speed. The text and the way on open after one listen. */
@Composable
fun DialoguePlayer(graph: AppGraph, lines: List<ListeningLine>, intro: String, nextLabel: String, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf<Job?>(null) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var listened by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val partner = remember { partnerVoice(graph) }
    val isPlaying = playing?.isActive == true

    MetCard {
        Text(intro)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0.8f, 1.0f, 1.2f).forEach { option ->
                FilterChip(selected = speed == option, onClick = { speed = option }, label = { Text("$option x") })
            }
        }
        Button(
            onClick = {
                if (isPlaying) {
                    playing?.cancel()
                    playing = null
                } else {
                    playing = scope.launch {
                        val queue = Channel<Utterance>(Channel.UNLIMITED)
                        lines.forEach { line ->
                            queue.trySend(Utterance(line.text, if (line.speaker == 0) graph.settings.voice else partner, speed))
                        }
                        queue.close()
                        runCatching { graph.speech.speak(queue) }
                        listened = true
                        playing = null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (isPlaying) "To'xtatish" else if (listened) "Yana tinglash" else "Tinglash")
        }
    }
    if (listened) {
        OutlinedButton(onClick = { showText = !showText }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showText) "Matnni yashirish" else "Matnni ko'rsatish")
        }
        if (showText) {
            MetCard {
                lines.forEach { line ->
                    Text((if (line.speaker == 0) "A: " else "B: ") + line.text)
                }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(nextLabel) }
    }
}

/** Hear a sentence, repeat it, and see which words the recognizer picked up. A coarse pronunciation check. */
@Composable
fun ShadowingStep(graph: AppGraph, sentence: String, position: String, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var matches by remember(sentence) { mutableStateOf<List<WordMatch>?>(null) }
    var error by remember(sentence) { mutableStateOf<String?>(null) }
    val voice = remember(sentence) {
        VoiceInputController(graph, scope, maxSeconds = 30, onFailure = { error = it }) { captured ->
            val result = compareWords(sentence, captured.text)
            matches = result
            scope.launch {
                graph.repo.addSpeaking(null, captured.speechSeconds, wordsOf(captured.text).size, captured.pauses)
                if (matchScore(result) >= 80) graph.repo.addXp(Xp.SHADOWING_SENTENCE)
            }
        }
    }
    LaunchedEffect(sentence) { runCatching { graph.speech.say(sentence) } }
    Text("Eshiting va qaytaring · $position", style = MaterialTheme.typography.labelLarge)
    MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sentence, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ListenButton(graph, sentence)
        }
    }
    error?.let { ErrorBanner(it) }
    matches?.let {
        MetCard {
            WordMatchText(it)
            Text("${matchScore(it)}% so'z tanildi. Qizil so'zlarni dastur aniq eshitmadi, ularning talaffuzini mashq qiling.", style = MaterialTheme.typography.bodySmall)
        }
    }
    MicButton(voice, idleLabel = if (matches == null) "Qaytarish uchun bosing" else "Yana urinish uchun bosing")
    if (matches != null) Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Keyingisi") }
}

@Composable
private fun DictationStep(graph: AppGraph, sentence: String, position: String, onNext: () -> Unit) {
    var typed by remember(sentence) { mutableStateOf("") }
    var matches by remember(sentence) { mutableStateOf<List<WordMatch>?>(null) }
    LaunchedEffect(sentence) { runCatching { graph.speech.say(sentence) } }
    Text("Diktant · $position", style = MaterialTheme.typography.labelLarge)
    MetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Eshitganingizni yozing", modifier = Modifier.weight(1f))
            ListenButton(graph, sentence)
            ListenButton(graph, sentence, speed = 0.8f)
        }
    }
    OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), enabled = matches == null)
    val result = matches
    if (result == null) {
        Button(onClick = { matches = compareWords(sentence, typed) }, enabled = typed.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Tekshirish") }
    } else {
        MetCard {
            WordMatchText(result)
            Text("${matchScore(result)}% to'g'ri", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Keyingisi") }
    }
}

/** Real speech for free: the learner's own file is transcribed on the phone and turned into exercises. */
@Composable
fun ImportAudioScreen(graph: AppGraph, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var transcript by remember { mutableStateOf<String?>(null) }
    var exercises by remember { mutableStateOf<ImportedAudioExercises?>(null) }
    var index by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        uri = picked
        error = null
        scope.launch {
            try {
                status = "Audio o'qilmoqda..."
                val samples = withContext(Dispatchers.Default) { AudioDecoder.decode(context, picked) }
                status = "Matnga aylantirilmoqda..."
                progress = 0f
                val text = withContext(Dispatchers.Default) {
                    graph.engines.withStt { stt -> stt.transcribe(samples) { progress = it } }
                }
                transcript = text
                status = "Mashqlar tuzilmoqda..."
                val model = graph.settings.tutorModel
                val level = graph.sessionLevel(null)
                val built = graph.claude {
                    it.structured(
                        "import", model, Effort.MEDIUM, Prompts.importedAudioSystem,
                        Prompts.importedAudioUser(text, level), ImportedAudioExercises.serializer(),
                    )
                }
                graph.repo.addCards(built.newWords)
                exercises = built
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = ClaudeGateway.describe(e)
            }
            status = null
        }
    }

    ScreenFrame(title = exercises?.title ?: "O'z audiongiz", onBack = nav::back) {
        ScrollArea {
            error?.let { ErrorBanner(it) }
            val built = exercises
            val text = transcript
            when {
                status != null -> {
                    LoadingBlock(status.orEmpty())
                    if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                built != null && text != null -> {
                    uri?.let { AudioFilePlayer(it) }
                    var showText by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showText = !showText }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showText) "Matnni yashirish" else "Matnni ko'rsatish")
                    }
                    if (showText) MetCard { Text(text) }
                    val question = built.questions.getOrNull(index)
                    if (question != null) {
                        SpokenQuestion(
                            graph, question.question, question.modelAnswer,
                            heading = {
                                Text("Savol ${index + 1} / ${built.questions.size}", style = MaterialTheme.typography.labelLarge)
                                MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(question.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            xpIfCorrect = Xp.LISTENING_ANSWER,
                            nextLabel = "Keyingisi",
                            onNext = { index++ },
                        )
                    } else {
                        MetCard(color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Tayyor. ${built.newWords.size} ta yangi so'z lug'atga qo'shildi.", fontWeight = FontWeight.SemiBold)
                        }
                        Button(onClick = nav::back, modifier = Modifier.fillMaxWidth()) { Text("Davom etish") }
                    }
                }
                else -> {
                    MetCard {
                        Text("Podcast, video yoki yozuvning audio faylini tanlang (ko'pi bilan ${AudioDecoder.MAX_SECONDS / 60} daqiqa). Fayl telefondan chiqmaydi, Claude'ga faqat matni boradi.")
                    }
                    Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Fayl tanlash") }
                }
            }
        }
    }
}

@Composable
private fun AudioFilePlayer(uri: Uri) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    val player = remember(uri) {
        runCatching { MediaPlayer.create(context, uri) }.getOrNull()?.apply { setOnCompletionListener { playing = false } }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    if (player != null) {
        Button(
            onClick = {
                if (playing) player.pause() else player.start()
                playing = !playing
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (playing) "To'xtatish" else "Audioni tinglash")
        }
    }
}
