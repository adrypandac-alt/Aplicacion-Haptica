package com.tesis.audiotohaptics.synthesis

import com.tesis.audiotohaptics.audio.Pcm16MonoWavWriter
import com.tesis.audiotohaptics.config.EnvelopeConfig
import com.tesis.audiotohaptics.config.OverlapPolicy
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/** Intervalo de actividad en segundos respecto al inicio de la canción original. */
data class HapticEvent(val startS: Double, val endS: Double, val velocity: Int = 127) {
    val durationS: Double get() = endS - startS
}

/** Resumen de la síntesis de un archivo. */
data class SynthesisReport(val samples: Int, val peakPcm: Int, val truncatedEvents: Int, val activeEvents: Int)

/**
 * Envolvente temporal + portadora de frecuencia FIJA -> WAV mono PCM 16 bits.
 * Port de Practica/src/vibrotactil/synthesis.py con el mismo diseño:
 *
 * - La envolvente es > 0 exactamente en las muestras [round(inicio*fs), round(fin*fs)):
 *   la rampa de subida empieza en el inicio y la de bajada termina en el fin, así el
 *   estímulo nunca se adelanta ni se alarga. Si la nota es más corta que ambas rampas,
 *   se reparten proporcionalmente.
 * - Política MAX: las notas simultáneas no se suman (nunca hay clipping).
 *   SUM_NORMALIZED: se suman y se escala globalmente por el pico (2 pasadas).
 * - Portadora con fase continua en todo el archivo; no depende de la altura de la nota.
 * - Se escribe por bloques para no crear arreglos del tamaño de la canción.
 */
class SignalSynthesizer(private val envelope: EnvelopeConfig, private val sampleRate: Int) {

    private data class SampleEvent(val start: Int, val end: Int, val gain: Double, val attack: Int, val release: Int)

    fun synthesize(
        events: List<HapticEvent>,
        totalSamples: Int,
        carrierHz: Double,
        maxAmplitude: Double,
        output: File,
    ): SynthesisReport {
        require(maxAmplitude > 0 && maxAmplitude <= 1.0)
        val (prepared, truncated) = prepare(events, totalSamples)
        val scale = if (envelope.overlapPolicy == OverlapPolicy.SUM_NORMALIZED) {
            maxOf(1.0, peakEnvelope(prepared, totalSamples))
        } else 1.0
        val block = ShortArray(BLOCK)
        val env = DoubleArray(BLOCK)
        var peak = 0
        val omega = 2.0 * PI * carrierHz / sampleRate
        Pcm16MonoWavWriter(output, sampleRate, totalSamples).use { writer ->
            var b0 = 0
            while (b0 < totalSamples) {
                val n = minOf(BLOCK, totalSamples - b0)
                fillEnvelope(prepared, b0, n, env)
                for (i in 0 until n) {
                    val v = maxAmplitude * (env[i] / scale) * sin(omega * (b0 + i))
                    val pcm = (v * FULL_SCALE).roundToInt()
                    check(pcm in -FULL_SCALE..FULL_SCALE) { "Clipping en la muestra ${b0 + i}" }
                    block[i] = pcm.toShort()
                    if (kotlin.math.abs(pcm) > peak) peak = kotlin.math.abs(pcm)
                }
                writer.write(block, n)
                b0 += n
            }
        }
        return SynthesisReport(totalSamples, peak, truncated, prepared.size)
    }

    /** Valor de la envolvente (antes de escalar) en [from, from+n): usado también para validar. */
    fun envelopeValues(events: List<HapticEvent>, totalSamples: Int, from: Int, n: Int): DoubleArray {
        val (prepared, _) = prepare(events, totalSamples)
        return DoubleArray(n).also { fillEnvelope(prepared, from, n, it) }
    }

    private fun prepare(events: List<HapticEvent>, totalSamples: Int): Pair<List<SampleEvent>, Int> {
        val attack = (envelope.attackS * sampleRate).roundToInt()
        val release = (envelope.releaseS * sampleRate).roundToInt()
        var truncated = 0
        val out = ArrayList<SampleEvent>()
        for (e in events) {
            var s = (e.startS * sampleRate).roundToLong()
            var t = (e.endS * sampleRate).roundToLong()
            if (t > totalSamples || s < 0) truncated++
            s = s.coerceAtLeast(0); t = t.coerceAtMost(totalSamples.toLong())
            if (t <= s) continue
            val len = (t - s).toInt()
            var a = attack
            var r = release
            if (a + r > len) {
                val total = a + r
                a = if (total > 0) (len.toLong() * attack / total).toInt() else 0
                r = len - a
            }
            out.add(SampleEvent(s.toInt(), t.toInt(), gain(e), a, r))
        }
        out.sortBy { it.start }
        return out to truncated
    }

    private fun gain(e: HapticEvent): Double {
        if (!envelope.mapVelocity) return 1.0
        val v = e.velocity.coerceIn(0, 127) / 127.0
        return envelope.velocityMinGain + (1 - envelope.velocityMinGain) * v
    }

    private fun fillEnvelope(events: List<SampleEvent>, from: Int, n: Int, env: DoubleArray) {
        java.util.Arrays.fill(env, 0, n, 0.0)
        val to = from + n
        for (e in events) {
            if (e.start >= to) break // ordenados por inicio
            if (e.end <= from) continue
            for (pos in maxOf(e.start, from) until minOf(e.end, to)) {
                val v = e.gain * shape(pos - e.start, e.end - e.start, e.attack, e.release)
                val i = pos - from
                env[i] = if (envelope.overlapPolicy == OverlapPolicy.MAX) maxOf(env[i], v) else env[i] + v
            }
        }
    }

    private fun peakEnvelope(events: List<SampleEvent>, totalSamples: Int): Double {
        val env = DoubleArray(BLOCK)
        var peak = 0.0
        var b0 = 0
        while (b0 < totalSamples) {
            val n = minOf(BLOCK, totalSamples - b0)
            fillEnvelope(events, b0, n, env)
            for (i in 0 until n) if (env[i] > peak) peak = env[i]
            b0 += n
        }
        return peak
    }

    companion object {
        const val FULL_SCALE = 32767
        private const val BLOCK = 1 shl 16

        /** Rampa de coseno elevado estrictamente en (0, 1): k-ésima de n muestras. */
        internal fun raisedCosine(k: Int, n: Int): Double = 0.5 * (1 - cos(PI * (k + 1) / (n + 1)))

        /** Forma de la envolvente de una nota de [length] muestras en la posición [k]. */
        internal fun shape(k: Int, length: Int, attack: Int, release: Int): Double {
            var v = 1.0
            if (k < attack) v *= raisedCosine(k, attack)
            val fromEnd = length - 1 - k
            if (fromEnd < release) v *= raisedCosine(fromEnd, release)
            return v
        }
    }
}
