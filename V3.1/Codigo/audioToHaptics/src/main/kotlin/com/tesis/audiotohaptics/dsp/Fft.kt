package com.tesis.audiotohaptics.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * FFT compleja radix-2 iterativa (in situ) para tamaños potencia de 2.
 *
 * Las tablas de giro y la permutación de bits se precalculan por tamaño; una
 * instancia no es segura entre hilos si se comparten los arreglos de trabajo.
 */
class Fft(val size: Int) {
    private val levels: Int
    private val cosTable = DoubleArray(size / 2)
    private val sinTable = DoubleArray(size / 2)
    private val bitReversed = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0) { "El tamaño de la FFT debe ser potencia de 2: $size" }
        levels = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size / 2) {
            cosTable[i] = cos(2.0 * PI * i / size)
            sinTable[i] = sin(2.0 * PI * i / size)
        }
        for (i in 0 until size) {
            bitReversed[i] = Integer.reverse(i) ushr (32 - levels)
        }
    }

    /** Transformada directa: X[k] = sum x[n] e^{-2πikn/N} (sin escalar). */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    /** Transformada inversa sin el factor 1/N. */
    fun inverseUnscaled(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = true)

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        require(re.size == size && im.size == size)
        for (i in 0 until size) {
            val j = bitReversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sign * sinTable[k]
                    val l = j + half
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += half * 2
            }
            half *= 2
        }
    }
}

/** Ventana de Hann periódica, igual que torch.hann_window y scipy get_window('hann'). */
fun hannPeriodic(n: Int): DoubleArray = DoubleArray(n) { 0.5 - 0.5 * cos(2.0 * PI * it / n) }

fun nextPowerOfTwo(n: Int): Int {
    var p = 1
    while (p < n) p = p shl 1
    return p
}
