package com.tesis.audiotohaptics.validation

import com.tesis.audiotohaptics.audio.WavReader
import com.tesis.audiotohaptics.dsp.Fft
import com.tesis.audiotohaptics.dsp.nextPowerOfTwo
import com.tesis.audiotohaptics.synthesis.HapticEvent
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln

@Serializable
data class ErrorStats(val meanAbsMs: Double, val p95AbsMs: Double, val maxAbsMs: Double)

@Serializable
data class ValidationReport(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val samples: Int,
    val peakPcm: Int,
    val clippedSamples: Int,
    val expectedSegments: Int,
    val detectedSegments: Int,
    val matchedSegments: Int,
    val startError: ErrorStats?,
    val endError: ErrorStats?,
    val measuredCarrierHz: Double?,
    /** Cota analítica del error de ESTA medición: rampa + 1/(2 fc) + 1/fs. */
    val measurementBoundMs: Double,
)

/**
 * Verificación digital de un WAV generado, analizado como artefacto independiente
 * (port de Practica/src/vibrotactil/validation.py):
 * - segmentos activos: |x| > 1 % de la amplitud máxima, uniendo huecos < 1 periodo;
 * - error de inicio/fin frente a la unión de los eventos de entrada;
 * - portadora: pico espectral (Hann, zero-padding x4, interpolación parabólica) sobre
 *   el segmento activo más largo (hasta 2^17 muestras).
 * No mide latencia BLE ni respuesta mecánica: eso requiere la prueba con hardware.
 */
object SignalValidator {
    const val THRESHOLD_RATIO = 0.01

    fun validate(
        wav: File,
        events: List<HapticEvent>,
        carrierHz: Double,
        maxAmplitude: Double,
        rampS: Double,
    ): ValidationReport {
        val info = WavReader.info(wav)
        val threshold = THRESHOLD_RATIO * maxAmplitude * 32767
        val maxGap = kotlin.math.ceil(info.sampleRate / carrierHz).toInt()
        val segments = ArrayList<IntArray>()
        var peak = 0
        var clipped = 0
        var segStart = -1
        var lastActive = -1
        forEachSample(wav) { i, v ->
            val a = abs(v.toInt())
            if (a > peak) peak = a
            if (a >= 32767) clipped++
            if (a > threshold) {
                if (segStart < 0) segStart = i
                else if (i - lastActive > maxGap) {
                    segments.add(intArrayOf(segStart, lastActive + 1)); segStart = i
                }
                lastActive = i
            }
        }
        if (segStart >= 0) segments.add(intArrayOf(segStart, lastActive + 1))
        val sr = info.sampleRate.toDouble()
        val detected = segments.map { it[0] / sr to it[1] / sr }
        val duration = info.frames / sr
        val expected = mergedIntervals(events, 1.0 / carrierHz)
            .filter { it.first < duration }.map { it.first to minOf(it.second, duration) }

        val startErr = ArrayList<Double>()
        val endErr = ArrayList<Double>()
        var d = 0
        for ((es, ee) in expected) {
            while (d < detected.size && detected[d].second <= es) d++
            var j = d
            val overlapping = ArrayList<Pair<Double, Double>>()
            while (j < detected.size && detected[j].first < ee) {
                if (detected[j].second > es) overlapping.add(detected[j])
                j++
            }
            if (overlapping.isEmpty()) continue
            startErr.add(overlapping.first().first - es)
            endErr.add(overlapping.last().second - ee)
        }
        val longest = segments.maxByOrNull { it[1] - it[0] }
        val carrier = longest?.let { estimateCarrier(wav, it[0], minOf(it[1] - it[0], 1 shl 17), info.sampleRate) }
        return ValidationReport(
            sampleRateHz = info.sampleRate, channels = info.channels, bitsPerSample = info.bitsPerSample,
            samples = info.frames, peakPcm = peak, clippedSamples = clipped,
            expectedSegments = expected.size, detectedSegments = detected.size, matchedSegments = startErr.size,
            startError = stats(startErr), endError = stats(endErr), measuredCarrierHz = carrier,
            measurementBoundMs = 1000 * (rampS + 1 / (2 * carrierHz) + 1 / sr),
        )
    }

    /** Unión de intervalos de actividad; une huecos <= maxGapS. */
    fun mergedIntervals(events: List<HapticEvent>, maxGapS: Double = 0.0): List<Pair<Double, Double>> {
        val merged = ArrayList<DoubleArray>()
        for (e in events.sortedBy { it.startS }) {
            if (merged.isNotEmpty() && e.startS <= merged.last()[1] + maxGapS) {
                merged.last()[1] = maxOf(merged.last()[1], e.endS)
            } else merged.add(doubleArrayOf(e.startS, e.endS))
        }
        return merged.map { it[0] to it[1] }
    }

    fun estimateCarrier(samples: DoubleArray, sampleRate: Int): Double? {
        if (samples.size < 8 || samples.all { it == 0.0 }) return null
        val n = nextPowerOfTwo(samples.size * 4)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in samples.indices) re[i] = samples[i] * (0.5 - 0.5 * cos(2 * PI * i / (samples.size - 1)))
        Fft(n).forward(re, im)
        val mag = DoubleArray(n / 2 + 1) { kotlin.math.hypot(re[it], im[it]) }
        var k = 1
        for (i in 1 until mag.size) if (mag[i] > mag[k]) k = i
        var kf = k.toDouble()
        if (k in 1 until mag.size - 1) {
            val a = ln(mag[k - 1] + 1e-300); val b = ln(mag[k] + 1e-300); val c = ln(mag[k + 1] + 1e-300)
            kf = k + 0.5 * (a - c) / (a - 2 * b + c)
        }
        return kf * sampleRate / n
    }

    private fun estimateCarrier(wav: File, start: Int, count: Int, sampleRate: Int): Double? {
        val buf = DoubleArray(count)
        forEachSample(wav) { i, v -> if (i >= start && i < start + count) buf[i - start] = v.toDouble() }
        return estimateCarrier(buf, sampleRate)
    }

    private fun stats(values: List<Double>): ErrorStats? {
        if (values.isEmpty()) return null
        val ms = values.map { abs(it) * 1000 }.sorted()
        // Percentil 95 con interpolación lineal (np.percentile por defecto).
        val pos = 0.95 * (ms.size - 1)
        val lo = pos.toInt()
        val p95 = if (lo + 1 < ms.size) ms[lo] + (pos - lo) * (ms[lo + 1] - ms[lo]) else ms[lo]
        return ErrorStats(round4(ms.average()), round4(p95), round4(ms.last()))
    }

    private fun round4(v: Double) = Math.round(v * 10000) / 10000.0

    /** Recorre las muestras de un WAV mono PCM 16 sin cargarlo entero en memoria. */
    private inline fun forEachSample(wav: File, action: (Int, Short) -> Unit) {
        val info = WavReader.info(wav)
        require(info.channels == 1 && info.bitsPerSample == 16) { "Se esperaba WAV mono PCM 16" }
        val dataOffset = RandomAccessFile(wav, "r").use { it.length() - info.frames.toLong() * 2 }
        BufferedInputStream(FileInputStream(wav), 1 shl 16).use { input ->
            input.skip(dataOffset)
            val bytes = ByteArray(1 shl 15)
            var index = 0
            while (index < info.frames) {
                val want = minOf(bytes.size, (info.frames - index) * 2)
                var got = 0
                while (got < want) {
                    val r = input.read(bytes, got, want - got)
                    if (r < 0) break
                    got += r
                }
                var p = 0
                while (p + 1 < got) {
                    val v = ((bytes[p].toInt() and 0xFF) or (bytes[p + 1].toInt() shl 8)).toShort()
                    action(index++, v)
                    p += 2
                }
                if (got < want) break
            }
        }
    }
}
