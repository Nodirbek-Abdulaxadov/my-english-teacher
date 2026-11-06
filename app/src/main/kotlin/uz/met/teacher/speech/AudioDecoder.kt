package uz.met.teacher.speech

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteOrder

/** Reads the learner's own audio file into the 16 kHz mono samples Whisper takes. */
object AudioDecoder {
    const val MAX_SECONDS = 15 * 60

    fun decode(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        try {
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: throw IOException("Faylda audio topilmadi")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION) > MAX_SECONDS * 1_000_000L) {
                throw IOException("Audio juda uzun: ko'pi bilan ${MAX_SECONDS / 60} daqiqa bo'lsin")
            }
            return decodeTrack(extractor, format)
        } finally {
            extractor.release()
        }
    }

    private fun decodeTrack(extractor: MediaExtractor, format: MediaFormat): FloatArray {
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val pieces = mutableListOf<FloatArray>()
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatOutput = false
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val input = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (input >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(input)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val output = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val changed = codec.outputFormat
                    rate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatOutput = changed.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        changed.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (output >= 0) {
                    val buffer = codec.getOutputBuffer(output)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val frames = if (floatOutput) {
                        val view = buffer.asFloatBuffer()
                        FloatArray(view.remaining() / channels) { f -> mix(channels) { c -> view.get(f * channels + c) } }
                    } else {
                        val view = buffer.asShortBuffer()
                        FloatArray(view.remaining() / channels) { f -> mix(channels) { c -> view.get(f * channels + c) / 32768f } }
                    }
                    pieces += frames
                    codec.releaseOutputBuffer(output, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }
        val joined = FloatArray(pieces.sumOf { it.size })
        var offset = 0
        for (piece in pieces) {
            piece.copyInto(joined, offset)
            offset += piece.size
        }
        return resample(joined, rate, MIC_SAMPLE_RATE)
    }

    private inline fun mix(channels: Int, sample: (Int) -> Float): Float {
        var sum = 0f
        for (c in 0 until channels) sum += sample(c)
        return sum / channels
    }

    private fun resample(samples: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || samples.isEmpty()) return samples
        val out = FloatArray((samples.size.toLong() * to / from).toInt())
        val step = from.toDouble() / to
        for (i in out.indices) {
            val position = i * step
            val left = position.toInt()
            val right = minOf(left + 1, samples.lastIndex)
            val mix = (position - left).toFloat()
            out[i] = samples[left] * (1 - mix) + samples[right] * mix
        }
        return out
    }

    private const val TIMEOUT_US = 10_000L
}
