package uz.met.teacher.speech

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.abs

const val MIC_SAMPLE_RATE = 16_000

/** Records the microphone from [start] until [stop]. The caller must hold RECORD_AUDIO. */
class MicRecorder(private val testRecording: File? = null) {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val chunks = mutableListOf<FloatArray>()
    @Volatile private var running = false

    /** Loudness of the latest audio from 0 to 1, for showing that the microphone hears something. */
    @Volatile var level = 0f
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        chunks.clear()
        if (testRecording != null) {
            running = true
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(MIC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MIC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            minBuffer * 4,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Mikrofon ochilmadi" }
        running = true
        record = recorder
        recorder.startRecording()
        worker = thread(name = "mic") {
            val buffer = FloatArray(MIC_SAMPLE_RATE / 10)
            while (running) {
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    chunks += buffer.copyOf(read)
                    var peak = 0f
                    for (i in 0 until read) peak = maxOf(peak, abs(buffer[i]))
                    level = peak.coerceAtMost(1f)
                }
            }
        }
    }

    fun stop(): FloatArray {
        running = false
        worker?.join()
        worker = null
        record?.run { stop(); release() }
        record = null
        level = 0f
        testRecording?.let { return readWavMono16(it) }
        val samples = FloatArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(samples, offset)
            offset += chunk.size
        }
        chunks.clear()
        return samples
    }
}

/** Reads a 16-bit mono WAV, the only format test recordings use. */
private fun readWavMono16(file: File): FloatArray {
    val bytes = file.readBytes()
    val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var offset = 12
    while (offset + 8 <= bytes.size) {
        val id = String(bytes, offset, 4)
        val size = data.getInt(offset + 4)
        if (id == "data") {
            val count = minOf(size, bytes.size - offset - 8) / 2
            return FloatArray(count) { i -> data.getShort(offset + 8 + i * 2) / 32768f }
        }
        offset += 8 + size
    }
    return FloatArray(0)
}

/** Plays synthesized speech. Writes block once the small buffer is full, so playback paces them. */
class PcmPlayer(sampleRate: Int) {
    private val bufferBytes = maxOf(
        AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
        sampleRate * BYTES_PER_SAMPLE / 5,
    )
    private val bufferMs = bufferBytes * 1000L / (sampleRate * BYTES_PER_SAMPLE)
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(bufferBytes)
        .build()
        .also { it.play() }

    fun write(samples: FloatArray, offset: Int, count: Int) {
        track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
    }

    /**
     * Lets the buffered tail play out, then frees the track. The wait is timed because the
     * playback position reads 0 once a track is stopped; at most one buffer can still be queued.
     */
    fun finish() {
        track.stop()
        Thread.sleep(bufferMs + 150)
        track.release()
    }

    /** Stops right now, dropping whatever is still queued. */
    fun abort() {
        track.pause()
        track.flush()
        track.release()
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 4
    }
}
