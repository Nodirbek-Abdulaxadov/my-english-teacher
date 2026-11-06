package uz.met.teacher.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.met.core.AudioAnalysis
import uz.met.teacher.AppGraph
import uz.met.teacher.speech.MIC_SAMPLE_RATE

data class Captured(
    val text: String,
    val audioSeconds: Double,
    val speechSeconds: Double,
    val pauses: Int,
    val sttMs: Long,
    /** System.nanoTime() when the learner stopped, the start of the latency we care about. */
    val stoppedAtNanos: Long,
)

/** Records one spoken answer and turns it into text. The screens only start and stop it. */
class VoiceInputController(
    private val graph: AppGraph,
    private val scope: CoroutineScope,
    private val maxSeconds: Int = 300,
    private val onFailure: (String) -> Unit,
    private val onCaptured: (Captured) -> Unit,
) {
    enum class State { IDLE, RECORDING, TRANSCRIBING }

    var state by mutableStateOf(State.IDLE)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var seconds by mutableIntStateOf(0)
        private set

    private val recorder = graph.newRecorder()
    private var ticker: Job? = null

    fun start() {
        if (state != State.IDLE) return
        runCatching { recorder.start() }
            .onFailure {
                onFailure("Mikrofon ochilmadi: ${it.message}")
                return
            }
        state = State.RECORDING
        val startedAt = System.currentTimeMillis()
        seconds = 0
        ticker = scope.launch {
            while (true) {
                delay(80)
                level = recorder.level
                seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                if (seconds >= maxSeconds) stop()
            }
        }
    }

    /** Closes the microphone and throws the recording away, for when the screen goes away mid-recording. */
    fun cancel() {
        if (state != State.RECORDING) return
        ticker?.cancel()
        level = 0f
        recorder.stop()
        state = State.IDLE
    }

    fun stop() {
        if (state != State.RECORDING) return
        ticker?.cancel()
        level = 0f
        val stoppedAt = System.nanoTime()
        val samples = recorder.stop()
        state = State.TRANSCRIBING
        scope.launch {
            try {
                val stats = withContext(Dispatchers.Default) { AudioAnalysis.stats(samples, MIC_SAMPLE_RATE) }
                if (stats.speechSeconds < MIN_SPEECH_SECONDS) {
                    onFailure("Ovozingiz eshitilmadi. Yana bir bor urinib ko'ring.")
                    return@launch
                }
                val text = withContext(Dispatchers.Default) { graph.engines.withStt { it.transcribe(samples) } }
                if (text.isBlank()) {
                    onFailure("Nutq tanilmadi. Yana bir bor urinib ko'ring.")
                    return@launch
                }
                state = State.IDLE
                onCaptured(
                    Captured(
                        text = text,
                        audioSeconds = samples.size.toDouble() / MIC_SAMPLE_RATE,
                        speechSeconds = stats.speechSeconds,
                        pauses = stats.pauses,
                        sttMs = (System.nanoTime() - stoppedAt) / 1_000_000,
                        stoppedAtNanos = stoppedAt,
                    ),
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onFailure("Nutqni tanishda xato: ${e.message}")
            } finally {
                state = State.IDLE
            }
        }
    }

    private companion object {
        const val MIN_SPEECH_SECONDS = 0.3
    }
}
