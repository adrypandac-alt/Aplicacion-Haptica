package com.tesis.audiotohaptics.dsp

import com.tesis.audiotohaptics.storage.FloatSignal
import com.tesis.audiotohaptics.storage.MutableFloatSignal
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Remuestreo de razón arbitraria con sinc enventanado (Kaiser), tabulado e
 * interpolado linealmente. Port de tools/model_export/reference_dsp.py.
 *
 * Validación (PC): SDR 43-70 dB frente a soxr_hq (el remuestreador del prototipo);
 * Basic Pitch con este remuestreador reproduce el CSV del prototipo con F1 >= 0,991.
 */
object Resampler {
    const val ZERO_CROSSINGS = 32
    const val ROLLOFF = 0.945
    const val KAISER_BETA = 8.6
    const val PHASES = 512

    private val table: DoubleArray by lazy { buildTable() }

    fun outputLength(inputLength: Int, fin: Int, fout: Int): Int =
        ceil(inputLength.toDouble() * fout / fin).toInt()

    /** Remuestrea [input] (fin Hz) en [output] (fout Hz); output.size debe ser outputLength(...). */
    fun resample(input: FloatSignal, fin: Int, fout: Int, output: MutableFloatSignal) {
        val nOut = outputLength(input.size, fin, fout)
        require(output.size >= nOut) { "Salida demasiado corta: ${output.size} < $nOut" }
        if (fin == fout) {
            for (i in 0 until input.size) output[i] = input[i]
            return
        }
        val scale = minOf(1.0, fout.toDouble() / fin) * ROLLOFF
        val half = ZERO_CROSSINGS / scale
        val reach = ceil(half).toInt()
        val step = fin.toDouble() / fout
        val tableLast = table.size - 1
        val n = input.size
        for (o in 0 until nOut) {
            val t = o * step
            val k0 = floor(t).toInt()
            var acc = 0.0
            val kStart = maxOf(0, k0 - reach)
            val kEnd = minOf(n - 1, k0 + reach)
            for (k in kStart..kEnd) {
                val dist = kotlin.math.abs(t - k) * scale * PHASES
                val idx = dist.toInt()
                if (idx >= tableLast) continue
                val frac = dist - idx
                val h = table[idx] + frac * (table[idx + 1] - table[idx])
                acc += input[k] * h
            }
            output[o] = (acc * scale).toFloat()
        }
    }

    private fun buildTable(): DoubleArray {
        val len = ZERO_CROSSINGS * PHASES + 1
        // np.kaiser(2*len-1, beta)[len-1:]: mitad derecha de una ventana simétrica.
        val m = 2 * len - 1
        val i0Beta = besselI0(KAISER_BETA)
        return DoubleArray(len) { i ->
            val t = i.toDouble() / PHASES
            val sinc = if (t == 0.0) 1.0 else sin(PI * t) / (PI * t)
            val n = (i + len - 1).toDouble()
            val r = 2.0 * n / (m - 1) - 1.0
            val w = besselI0(KAISER_BETA * sqrt(maxOf(0.0, 1.0 - r * r))) / i0Beta
            sinc * w
        }
    }

    /** Función de Bessel modificada de primera especie, orden 0 (serie de potencias). */
    internal fun besselI0(x: Double): Double {
        var sum = 1.0
        var term = 1.0
        val q = x * x / 4.0
        var k = 1
        while (term > 1e-17 * sum) {
            term *= q / (k.toDouble() * k)
            sum += term
            k++
        }
        return sum
    }
}
