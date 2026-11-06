package uz.met.teacher.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.met.core.BetterVersion
import uz.met.core.ClaudeGateway
import uz.met.core.Effort
import uz.met.core.Example
import uz.met.core.Level
import uz.met.core.Prompts
import uz.met.core.ReplyHints
import uz.met.core.SentenceSplitter
import uz.met.core.SessionAnalysis
import uz.met.core.TutorConversation
import uz.met.core.wordsOf
import uz.met.core.MetJson
import uz.met.teacher.AppGraph
import uz.met.teacher.data.SessionEntity
import uz.met.teacher.data.TurnEntity
import uz.met.teacher.speech.SpeakStats
import uz.met.teacher.speech.Utterance

data class ChatMessage(
    val id: Long,
    val fromLearner: Boolean,
    val text: String,
    val better: BetterVersion? = null,
    val betterLoading: Boolean = false,
)

/** Runs one spoken conversation: mic, Whisper, Claude's stream, speech, and saving as it goes. */
class ConversationController(
    private val graph: AppGraph,
    private val scope: CoroutineScope,
    private val title: String,
    private val brief: String?,
    private val lessonId: String?,
) {
    enum class Phase { STARTING, READY, THINKING, SPEAKING, REVIEW }

    var phase by mutableStateOf(Phase.STARTING)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** What Whisper heard, while the learner can still change it before sending. */
    var draft by mutableStateOf("")
    var timing by mutableStateOf<String?>(null)
        private set
    val messages = mutableStateListOf<ChatMessage>()
    /** Ready-made ways to reply, shown when the learner doesn't know what to say. */
    var hints by mutableStateOf<List<Example>?>(null)
        private set
    var hintsLoading by mutableStateOf(false)
        private set

    val voice = VoiceInputController(graph, scope, onFailure = { error = it }, onCaptured = ::onCaptured)

    private var sessionId = 0L
    private var conversation: TutorConversation? = null
    private var turn: Job? = null
    private var speaking: Job? = null
    private var pendingCapture: Captured? = null
    private var nextId = 0L
    private var level = graph.settings.profile.level
    private val speed get() = graph.settings.speed * Level.speechPace(level)

    fun start() {
        scope.launch {
            try {
                sessionId = graph.repo.dao.insertSession(
                    SessionEntity(kind = "CONVERSATION", title = title, lessonId = lessonId, startedAt = System.currentTimeMillis()),
                )
                level = graph.sessionLevel(lessonId)
                val system = Prompts.conversationSystem(graph.settings.profile, brief, level)
                val model = graph.settings.tutorModel
                conversation = graph.claude { it.conversation(model, system) }
                launchTurn(Prompts.KICKOFF, captured = null, showAsLearner = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = ClaudeGateway.describe(e)
                phase = Phase.READY
            }
        }
    }

    /** Starts listening, cutting the tutor off if it is still talking. */
    fun startListening() {
        speaking?.cancel()
        error = null
        voice.start()
    }

    private fun onCaptured(captured: Captured) {
        if (graph.settings.reviewBeforeSend) {
            pendingCapture = captured
            draft = captured.text
            phase = Phase.REVIEW
        } else {
            sendSpoken(captured, captured.text)
        }
    }

    fun confirmDraft() {
        val captured = pendingCapture ?: return
        pendingCapture = null
        sendSpoken(captured, draft.trim().ifEmpty { captured.text })
    }

    fun discardDraft() {
        pendingCapture = null
        draft = ""
        phase = Phase.READY
    }

    fun sendTyped(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        error = null
        scope.launch {
            turn?.join()
            graph.repo.addSpeaking(sessionId, 0.0, wordsOf(clean).size, 0)
            launchTurn(clean, captured = null, showAsLearner = true)
        }
    }

    private fun sendSpoken(captured: Captured, text: String) {
        scope.launch {
            // The tutor's previous reply may still be streaming after being cut off.
            turn?.join()
            graph.repo.addSpeaking(sessionId, captured.speechSeconds, wordsOf(text).size, captured.pauses)
            launchTurn(text, captured, showAsLearner = true)
        }
    }

    private fun launchTurn(text: String, captured: Captured?, showAsLearner: Boolean) {
        val chat = conversation
        if (chat == null) {
            error = "Suhbat boshlanmadi. Chiqib, qayta kiring."
            phase = Phase.READY
            return
        }
        turn = scope.launch {
            phase = Phase.THINKING
            error = null
            hints = null
            if (showAsLearner) {
                messages += ChatMessage(nextId++, true, text)
                graph.repo.dao.insertTurn(TurnEntity(sessionId = sessionId, fromLearner = true, text = text, at = System.currentTimeMillis(), sttMs = captured?.sttMs))
            }
            val startNanos = captured?.stoppedAtNanos ?: System.nanoTime()
            fun elapsedMs() = (System.nanoTime() - startNanos) / 1_000_000

            val replyIndex = messages.size
            messages += ChatMessage(nextId++, false, "")
            val queue = Channel<Utterance>(Channel.UNLIMITED)
            var firstAudioMs: Long? = null
            var stats = SpeakStats(0, 0.0)
            speaking = launch {
                stats = graph.speech.speak(queue, onFirstAudio = {
                    firstAudioMs = elapsedMs()
                    scope.launch { phase = Phase.SPEAKING }
                })
            }
            var firstTokenMs: Long? = null
            val splitter = SentenceSplitter()
            try {
                graph.repo.ensureBudget()
                val result = withContext(Dispatchers.IO) {
                    chat.send(text) { delta ->
                        if (firstTokenMs == null) firstTokenMs = elapsedMs()
                        scope.launch { messages[replyIndex] = messages[replyIndex].let { it.copy(text = it.text + delta) } }
                        splitter.feed(delta).forEach { queue.trySend(Utterance(it, speed = speed)) }
                    }
                }
                if (result.refused) error = "Claude bu gapga javob bermadi. Boshqacha aytib ko'ring."
                splitter.flush()?.let { queue.trySend(Utterance(it, speed = speed)) }
                queue.close()
                speaking?.join()
                val factor = if (stats.spokenSeconds > 0) stats.synthMs / 1000.0 / stats.spokenSeconds else null
                graph.repo.dao.insertTurn(
                    TurnEntity(
                        sessionId = sessionId,
                        fromLearner = false,
                        text = result.text,
                        at = System.currentTimeMillis(),
                        sttMs = captured?.sttMs,
                        firstTokenMs = firstTokenMs,
                        firstAudioMs = firstAudioMs,
                        ttsFactor = factor,
                        cacheRead = result.usage.cacheReadTokens,
                        config = "${graph.settings.tutorModel.name}/${graph.settings.stt.name}/${graph.settings.tts.name}",
                    ),
                )
                timing = buildString {
                    captured?.let { append("Whisper ${it.sttMs} ms · ") }
                    firstTokenMs?.let { append("Claude ${it} ms · ") }
                    firstAudioMs?.let { append("ovoz ${it} ms") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                queue.close()
                speaking?.cancel()
                error = ClaudeGateway.describe(e)
                if (messages.getOrNull(replyIndex)?.text.isNullOrEmpty()) messages.removeAt(replyIndex)
            } finally {
                phase = Phase.READY
            }
        }
    }

    /** Asks for a B2 version of what the learner said, and says it aloud. */
    fun improve(id: Long) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val original = messages[index]
        messages[index] = original.copy(betterLoading = true)
        val model = graph.settings.tutorModel
        scope.launch {
            try {
                val better = graph.claude {
                    it.structured(
                        "better", model, Effort.LOW, Prompts.betterVersionSystem,
                        "<transcript>${original.text}</transcript>", BetterVersion.serializer(),
                    )
                }
                messages[index] = original.copy(better = better)
                speaking?.cancel()
                speaking = launch { graph.speech.say(better.better, speed = speed) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                messages[index] = original
                error = ClaudeGateway.describe(e)
            }
        }
    }

    /** Asks for a few ways to reply. This is a separate request and leaves the conversation untouched. */
    fun requestHints() {
        if (hintsLoading) return
        hintsLoading = true
        error = null
        val recent = messages.takeLast(HINT_CONTEXT_TURNS).joinToString("\n") { (if (it.fromLearner) "Learner: " else "Tutor: ") + it.text }
        val model = graph.settings.tutorModel
        val currentLevel = level
        scope.launch {
            try {
                hints = graph.claude {
                    it.structured("hints", model, Effort.LOW, Prompts.hintSystem, Prompts.hintUser(recent, currentLevel), ReplyHints.serializer())
                }.options
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = ClaudeGateway.describe(e)
            }
            hintsLoading = false
        }
    }

    fun stopSpeaking() {
        speaking?.cancel()
    }

    /** Ends the session and returns its id for the summary, or null if nothing was said. */
    suspend fun finish(): Long? {
        speaking?.cancel()
        voice.cancel()
        turn?.join()
        val session = graph.repo.dao.session(sessionId) ?: return null
        if (session.words == 0) return null
        if (lessonId != null && session.words >= MIN_WORDS_FOR_LESSON) graph.repo.markLessonDone(lessonId)
        return sessionId
    }

    private companion object {
        const val MIN_WORDS_FOR_LESSON = 40
        const val HINT_CONTEXT_TURNS = 4
    }
}

/** Analyzes a finished session once and stores what came out of it. */
suspend fun analyzeSession(graph: AppGraph, sessionId: Long): SessionAnalysis {
    val turns = graph.repo.dao.turns(sessionId)
    val transcript = turns.joinToString("\n") { (if (it.fromLearner) "Learner: " else "Tutor: ") + it.text }
    val known = graph.settings.profile.recurringErrors
    val model = graph.settings.tutorModel
    val analysis = graph.claude {
        it.structured(
            "analysis", model, Effort.HIGH, Prompts.analysisSystem,
            Prompts.analysisUser(transcript, known), SessionAnalysis.serializer(),
        )
    }
    graph.repo.applyAnalysis(sessionId, analysis)
    return analysis
}

fun storedAnalysis(json: String?): SessionAnalysis? =
    json?.let { runCatching { MetJson.decodeFromString<SessionAnalysis>(it) }.getOrNull() }

