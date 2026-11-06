package com.tesis.audiotohaptics.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.tesis.audiotohaptics.config.HapticsException
import com.tesis.audiotohaptics.pipeline.DecodedAudio
import com.tesis.audiotohaptics.storage.MappedFloatArray
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodifica M4A/AAC, MP3, WAV y demás formatos que soporte el dispositivo mediante
 * MediaExtractor + MediaCodec (códecs del sistema, sin red). Escribe cada canal como
 * floats en archivos temporales y los mapea en memoria para no cargar el heap.
 *
 * Más de dos canales: se conservan los dos primeros. Un archivo mono se devuelve con un
 * solo canal (el pipeline lo duplica).
 */
internal class AndroidAudioDecoder(private val context: Context) {

    fun decode(uri: Uri, workDir: File, onProgress: (Double) -> Unit = {}, checkCancelled: () -> Unit = {}): DecodedAudio {
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                throw HapticsException.UnsupportedAudio("No se pudo abrir el archivo: ${e.message}", e)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw HapticsException.UnsupportedAudio("El archivo no contiene una pista de audio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
            val codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw HapticsException.UnsupportedAudio("No hay decodificador para $mime en este dispositivo", e)
            }
            return try {
                codec.configure(format, null, null, 0)
                codec.start()
                runDecoder(extractor, codec, workDir, durationUs, uri.lastPathSegment ?: "audio", onProgress, checkCancelled)
            } catch (e: HapticsException) {
                throw e
            } catch (e: Exception) {
                throw HapticsException.UnsupportedAudio("Error al decodificar $mime: ${e.message}", e)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun runDecoder(
        extractor: MediaExtractor, codec: MediaCodec, workDir: File, durationUs: Long, name: String,
        onProgress: (Double) -> Unit, checkCancelled: () -> Unit,
    ): DecodedAudio {
        workDir.mkdirs()
        val files = listOf(File(workDir, "decoded_0.f32"), File(workDir, "decoded_1.f32"))
        val outs = files.map { BufferedOutputStream(FileOutputStream(it), 1 shl 16) }
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var channels = 0
        var sampleRate = 0
        var floatPcm = false
        var frames = 0L
        val scratch = ByteBuffer.allocate(8 * 4096).order(ByteOrder.LITTLE_ENDIAN)
        try {
            while (!outputDone) {
                checkCancelled()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            if (durationUs > 0) onProgress((extractor.sampleTime.toDouble() / durationUs).coerceIn(0.0, 1.0))
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        if (channels == 0) {
                            val f = codec.outputFormat
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        val buf = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val bytesPerSample = if (floatPcm) 4 else 2
                        val n = info.size / (bytesPerSample * channels)
                        for (fr in 0 until n) {
                            for (c in 0 until minOf(channels, 2)) {
                                val p = info.offset + (fr * channels + c) * bytesPerSample
                                val v = if (floatPcm) buf.getFloat(p) else buf.getShort(p) / 32768f
                                scratch.clear(); scratch.putFloat(v)
                                outs[c].write(scratch.array(), 0, 4)
                            }
                        }
                        frames += n
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            outs.forEach { it.close() }
        }
        if (frames == 0L || sampleRate <= 0 || channels <= 0) {
            throw HapticsException.UnsupportedAudio("El archivo no produjo muestras de audio decodificables")
        }
        if (frames > Int.MAX_VALUE) throw HapticsException.UnsupportedAudio("Audio demasiado largo")
        val used = minOf(channels, 2)
        files.drop(used).forEach { it.delete() }
        val signals = (0 until used).map { MappedFloatArray.open(files[it], frames.toInt(), deleteOnClose = true) }
        return DecodedAudio(signals, sampleRate, name)
    }

    private companion object { const val TIMEOUT_US = 10_000L }
}
