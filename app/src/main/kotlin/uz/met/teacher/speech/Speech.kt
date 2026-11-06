package uz.met.teacher.speech

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.met.core.AudioAnalysis
import uz.met.teacher.data.Settings
import java.io.File

private const val THREADS = 4

enum class SttChoice(val label: String, val noteUz: String, val pack: ModelPack) {
    BASE("Whisper base", "Tezroq, 160 MB", ModelCatalog.whisperBase),
    SMALL("Whisper small", "Aniqroq, lekin sekinroq, 375 MB", ModelCatalog.whisperSmall),
}

enum class TtsChoice(val label: String, val noteUz: String, val pack: ModelPack, val voiceCount: Int) {
    SUPERTONIC("Supertonic", "Eng tez, 145 MB", ModelCatalog.supertonic, 10),
    KOKORO("Kokoro", "Sekinroq, lug'atda yo'q so'zlarni aytmaydi, 150 MB", ModelCatalog.kokoro, 28),
}

class SpeechToText(pack: ModelPack, dir: File) {
    private val recognizer = OfflineRecognizer(
        null,
        OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = MIC_SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(dir, pack.files[0].name).path,
                    decoder = File(dir, pack.files[1].name).path,
                    language = "en",
                    task = "transcribe",
                ),
                tokens = File(dir, pack.files[2].name).path,
                modelType = "whisper",
                numThreads = THREADS,
            ),
            decodingMethod = "greedy_search",
        ),
    )

    /** Handles audio of any length by working through it in pieces Whisper can take. */
    fun transcribe(samples: FloatArray, onProgress: (Float) -> Unit = {}): String {
        val pieces = AudioAnalysis.splitForWhisper(samples, MIC_SAMPLE_RATE)
        val text = StringBuilder()
        pieces.forEachIndexed { index, range ->
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples.copyOfRange(range.first, range.last + 1), MIC_SAMPLE_RATE)
                recognizer.decode(stream)
                if (text.isNotEmpty()) text.append(' ')
                text.append(recognizer.getResult(stream).text.trim())
            } finally {
                stream.release()
            }
            onProgress((index + 1f) / pieces.size)
        }
        return text.toString().trim()
    }

    fun release() = recognizer.release()
}

class TextToSpeech(choice: TtsChoice, dir: File) {
    private val tts = OfflineTts(
        null,
        OfflineTtsConfig(
            model = when (choice) {
                TtsChoice.SUPERTONIC -> OfflineTtsModelConfig(
                    supertonic = OfflineTtsSupertonicModelConfig(
                        durationPredictor = File(dir, "duration_predictor.int8.onnx").path,
                        textEncoder = File(dir, "text_encoder.int8.onnx").path,
                        vectorEstimator = File(dir, "vector_estimator.int8.onnx").path,
                        vocoder = File(dir, "vocoder.int8.onnx").path,
                        ttsJson = File(dir, "tts.json").path,
                        unicodeIndexer = File(dir, "unicode_indexer.bin").path,
                        voiceStyle = File(dir, "voice.bin").path,
                    ),
                    numThreads = THREADS,
                )
                // Kokoro reads pronunciations from a lexicon and silently skips any word not in it.
                TtsChoice.KOKORO -> OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = File(dir, "model.int8.onnx").path,
                        voices = File(dir, "voices.bin").path,
                        tokens = File(dir, "tokens.txt").path,
                        lexicon = File(dir, "lexicon-us-en.txt").path,
                    ),
                    numThreads = THREADS,
                )
            },
        ),
    )

    val sampleRate: Int get() = tts.sampleRate()

    fun synthesize(text: String, voice: Int, speed: Float): FloatArray = tts.generate(text, voice, speed).samples

    fun release() = tts.release()
}

/**
 * Loads the chosen engines once and reloads them only when a setting changes. They use a lot of
 * memory and aren't thread-safe, so every use goes through these functions, one at a time.
 */
class SpeechEngines(private val store: ModelStore, private val settings: Settings) {
    private val sttLock = Mutex()
    private val ttsLock = Mutex()
    private var stt: Pair<SttChoice, SpeechToText>? = null
    private var tts: Pair<TtsChoice, TextToSpeech>? = null

    suspend fun <T> withStt(block: (SpeechToText) -> T): T = sttLock.withLock {
        val choice = settings.stt
        val engine = stt?.takeIf { it.first == choice }?.second ?: run {
            stt?.second?.release()
            stt = null
            SpeechToText(choice.pack, store.dir(choice.pack)).also { stt = choice to it }
        }
        block(engine)
    }

    suspend fun <T> withTts(block: (TextToSpeech) -> T): T = ttsLock.withLock {
        val choice = settings.tts
        val engine = tts?.takeIf { it.first == choice }?.second ?: run {
            tts?.second?.release()
            tts = null
            TextToSpeech(choice, store.dir(choice.pack)).also { tts = choice to it }
        }
        block(engine)
    }
}
