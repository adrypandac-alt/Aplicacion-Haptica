package com.rommelguerra.prototipomusichaptic.integracion

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/** Adaptadores Android. Los algoritmos reciben solo BloqueAudio, sin depender de estas APIs. */
class EntradaAudioAndroid(private val context: Context) {
    suspend fun leerArchivo(uri: Uri, recibir: suspend (BloqueAudio) -> Unit) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("El archivo no contiene audio compatible")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()
            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val size = extractor.readSampleData(decoder.getInputBuffer(inputIndex)!!, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = decoder.dequeueOutputBuffer(info, 10000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output = decoder.outputFormat
                    sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) { "Formato PCM no soportado" }
                } else if (outputIndex >= 0) {
                    try {
                        if (info.size > 0) {
                            val output = decoder.getOutputBuffer(outputIndex)!!.order(ByteOrder.LITTLE_ENDIAN)
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val mono = FloatArray(info.size / (bytesPerSample * channels)) {
                                var sum = 0f
                                repeat(channels) { sum += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) output.float else output.short / 32768f }
                                (sum / channels).coerceIn(-1f, 1f)
                            }
                            recibir(BloqueAudio(mono, sampleRate, (info.presentationTimeUs / 1000).coerceAtLeast(0)))
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { decoder.releaseOutputBuffer(outputIndex, false) }
                }
            }
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }

    /** La pantalla solicita RECORD_AUDIO antes de llamar. Cancelar la corrutina libera el micrófono. */
    @SuppressLint("MissingPermission")
    suspend fun escuchar(recibir: suspend (BloqueAudio) -> Unit) = withContext(Dispatchers.IO) {
        val rate = 16000
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0) { "Micrófono no compatible con 16000 Hz" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 8192))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "No se pudo iniciar el micrófono" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "No se pudo acceder al micrófono" }
            val buffer = ShortArray(2048)
            var samples = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = recorder.read(buffer, 0, buffer.size)
                check(count > 0) { "Se interrumpió la captura de audio ($count)" }
                recibir(BloqueAudio(FloatArray(count) { buffer[it] / 32768f }, rate, samples * 1000 / rate))
                samples += count
            }
        } finally { runCatching { recorder.stop() }; recorder.release() }
    }
}
