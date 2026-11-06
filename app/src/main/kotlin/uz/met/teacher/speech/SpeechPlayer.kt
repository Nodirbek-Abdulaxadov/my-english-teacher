package uz.met.teacher.speech

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.met.teacher.data.Settings
import kotlin.coroutines.coroutineContext

/** A piece of text to say. Without a voice or speed, the learner's settings apply. */
data class Utterance(val text: String, val voice: Int? = null, val speed: Float? = null)

data class SpeakStats(val synthMs: Long, val spokenSeconds: Double)

/** Turns text into sound on the phone. Cancelling the calling coroutine stops the sound at once. */
class SpeechPlayer(private val engines: SpeechEngines, private val settings: Settings) {

    /**
     * Says everything from [utterances] until the channel closes. Two workers, so the next sentence
     * is synthesized while the current one is still playing, leaving no gap between sentences.
     */
    suspend fun speak(utterances: ReceiveChannel<Utterance>, onFirstAudio: () -> Unit = {}): SpeakStats = coroutineScope {
        val speech = Channel<FloatArray>(Channel.UNLIMITED)
        var synthMs = 0L
        var samples = 0L
        var rate = 0
        launch(Dispatchers.Default) {
            try {
                for (utterance in utterances) {
                    val text = utterance.text.trim()
                    if (text.isEmpty()) continue
                    val before = System.nanoTime()
                    val pcm = engines.withTts { tts ->
                        rate = tts.sampleRate
                        tts.synthesize(text, utterance.voice ?: settings.voice, utterance.speed ?: settings.speed)
                    }
                    synthMs += (System.nanoTime() - before) / 1_000_000
                    samples += pcm.size
                    speech.send(pcm)
                }
            } finally {
                speech.close()
            }
        }
        launch(Dispatchers.IO) { playAll(speech, onFirstAudio) { rate } }.join()
        SpeakStats(synthMs, if (rate > 0) samples.toDouble() / rate else 0.0)
    }

    suspend fun say(text: String, voice: Int? = null, speed: Float? = null) {
        val queue = Channel<Utterance>(1)
        queue.send(Utterance(text, voice, speed))
        queue.close()
        speak(queue)
    }

    private suspend fun playAll(speech: ReceiveChannel<FloatArray>, onFirstAudio: () -> Unit, rate: () -> Int) {
        var player: PcmPlayer? = null
        try {
            for (pcm in speech) {
                val active = player ?: PcmPlayer(rate()).also {
                    player = it
                    onFirstAudio()
                }
                // Small pieces, because a blocking write can't notice cancellation.
                var offset = 0
                while (offset < pcm.size) {
                    coroutineContext.ensureActive()
                    val count = minOf(rate() / 10, pcm.size - offset)
                    active.write(pcm, offset, count)
                    offset += count
                }
            }
            withContext(Dispatchers.IO) { player?.finish() }
        } catch (e: CancellationException) {
            player?.abort()
            throw e
        }
    }
}
