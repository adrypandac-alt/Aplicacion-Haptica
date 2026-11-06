package com.tesis.audiotohaptics.separation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.tesis.audiotohaptics.separation.DemucsSpectrogram.Companion.FRAMES
import com.tesis.audiotohaptics.separation.DemucsSpectrogram.Companion.FREQS
import com.tesis.audiotohaptics.separation.DemucsSpectrogram.Companion.SEGMENT
import com.tesis.audiotohaptics.storage.FloatSignal
import com.tesis.audiotohaptics.storage.MutableFloatSignal
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.sqrt

/** Fuentes de htdemucs_6s en el orden de salida del modelo. */
enum class DemucsSource(val index: Int) { DRUMS(0), BASS(1), OTHER(2), VOCALS(3), GUITAR(4), PIANO(5) }

/**
 * Separación de fuentes con htdemucs_6s.onnx (exportado por tools/model_export/export_demucs.py).
 *
 * Equivale a demucs.apply.apply_model(model, mix, shifts=0, split=True, overlap=0.25)
 * con la normalización de demucs.separate. shifts=0 hace el resultado determinista; el CLI
 * del prototipo usa shifts=1 (desplazamiento aleatorio), lo que explica diferencias de
 * 10-20 dB SDR entre ambos aun siendo el mismo modelo (docs/INFORME_VIABILIDAD.md).
 *
 * Memoria: medido en PC, ONNX Runtime necesita ~2,1 GB adicionales por segmento.
 */
class DemucsSeparator(modelFile: File, threads: Int = 0) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val spectrogram = DemucsSpectrogram()

    init {
        val options = OrtSession.SessionOptions().apply {
            if (threads > 0) setIntraOpNumThreads(threads)
            // El arena de memoria de ORT duplicaba el pico (5,5 GB frente a 2,5 GB en PC).
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
        }
        session = env.createSession(modelFile.absolutePath, options)
    }

    /**
     * Separa una mezcla estéreo a 44,1 kHz y acumula la versión MONO (media de canales)
     * de cada fuente pedida en [outputs]. [weightScratch] es un arreglo auxiliar de la
     * misma longitud. [onSegment] recibe (segmento actual, total) y puede lanzar
     * CancellationException para cancelar.
     */
    fun separate(
        left: FloatSignal,
        right: FloatSignal,
        outputs: Map<DemucsSource, MutableFloatSignal>,
        weightScratch: MutableFloatSignal,
        onSegment: (Int, Int) -> Unit = { _, _ -> },
    ) {
        val length = left.size
        require(right.size == length)
        outputs.values.forEach { require(it.size >= length) }

        // demucs.separate: normaliza con la media y la desviación (insesgada) del canal medio.
        var sum = 0.0
        for (i in 0 until length) sum += (left[i] + right[i]) * 0.5
        val mean = sum / length
        var sq = 0.0
        for (i in 0 until length) {
            val d = (left[i] + right[i]) * 0.5 - mean
            sq += d * d
        }
        val std = sqrt(sq / (length - 1)).coerceAtLeast(1e-8)

        val stride = ((1 - OVERLAP) * SEGMENT).toInt()
        val weight = DoubleArray(SEGMENT) { i ->
            val half = SEGMENT / 2
            (if (i < half) i + 1 else SEGMENT - i).toDouble() / half
        }
        val offsets = (0 until length step stride).toList()
        val segment = FloatArray(2 * SEGMENT)
        for ((k, offset) in offsets.withIndex()) {
            onSegment(k, offsets.size)
            val chunkLen = minOf(SEGMENT, length - offset)
            // TensorChunk.padded: el último segmento se centra con contexto real a la izquierda.
            val delta = SEGMENT - chunkLen
            val start = offset - delta / 2
            segment.fill(0f)
            for (i in 0 until SEGMENT) {
                val src = start + i
                if (src in 0 until length) {
                    segment[i] = ((left[src] - mean) / std).toFloat()
                    segment[SEGMENT + i] = ((right[src] - mean) / std).toFloat()
                }
            }
            runSegment(segment) { specOut, timeOut ->
                for ((source, out) in outputs) {
                    val l = spectrogram.inverse(specOut, source.index, 0)
                    val r = spectrogram.inverse(specOut, source.index, 1)
                    val tBase = source.index * 2 * SEGMENT
                    for (i in 0 until chunkLen) {
                        val j = i + delta / 2 // center_trim
                        val mono = 0.5 * (l[j] + timeOut.get(tBase + j) + r[j] + timeOut.get(tBase + SEGMENT + j))
                        out.add(offset + i, (weight[i] * mono).toFloat())
                    }
                }
            }
            for (i in 0 until chunkLen) weightScratch.add(offset + i, weight[i].toFloat())
        }
        onSegment(offsets.size, offsets.size)
        for (out in outputs.values) {
            for (i in 0 until length) out[i] = ((out[i] / weightScratch[i]) * std + mean).toFloat()
        }
    }

    // Buffers directos (fuera del heap de Java) reutilizados en todos los segmentos.
    // Las salidas se "fijan" (pinned outputs): ORT escribe en ellos sin copiar al heap.
    private val mixBuffer = directFloats(2 * SEGMENT)
    private val specBuffer = directFloats(4 * FREQS * FRAMES)
    private val specOutBuffer = directFloats(DemucsSpectrogram.SOURCES * 4 * FREQS * FRAMES)
    private val timeOutBuffer = directFloats(DemucsSpectrogram.SOURCES * 2 * SEGMENT)

    private fun runSegment(segment: FloatArray, consume: (FloatBuffer, FloatBuffer) -> Unit) {
        mixBuffer.clear(); mixBuffer.put(segment); mixBuffer.rewind()
        specBuffer.clear(); specBuffer.put(spectrogram.specInput(segment)); specBuffer.rewind()
        val f = FREQS.toLong()
        val t = FRAMES.toLong()
        val s = DemucsSpectrogram.SOURCES.toLong()
        OnnxTensor.createTensor(env, mixBuffer, longArrayOf(1, 2, SEGMENT.toLong())).use { mix ->
            OnnxTensor.createTensor(env, specBuffer, longArrayOf(1, 4, f, t)).use { spec ->
                OnnxTensor.createTensor(env, specOutBuffer, longArrayOf(1, s, 4, f, t)).use { specOut ->
                    OnnxTensor.createTensor(env, timeOutBuffer, longArrayOf(1, s, 2, SEGMENT.toLong())).use { timeOut ->
                        session.run(
                            mapOf("mix" to mix, "spec" to spec),
                            mapOf("spec_out" to specOut, "time_out" to timeOut),
                        ).close()
                        consume(specOutBuffer, timeOutBuffer)
                    }
                }
            }
        }
    }

    override fun close() = session.close()

    private fun directFloats(n: Int): FloatBuffer =
        java.nio.ByteBuffer.allocateDirect(n * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()

    companion object {
        const val OVERLAP = 0.25
    }
}
