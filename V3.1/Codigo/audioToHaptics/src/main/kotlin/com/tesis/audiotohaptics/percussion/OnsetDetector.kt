package com.tesis.audiotohaptics.percussion

import com.tesis.audiotohaptics.config.PercussionConfig
import com.tesis.audiotohaptics.dsp.Fft
import com.tesis.audiotohaptics.dsp.hannPeriodic
import com.tesis.audiotohaptics.storage.FloatSignal
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Golpe detectado: tiempo en segundos y nivel RMS relativo al máximo del stem. */
data class Onset(val timeS: Double, val relativeLevel: Double)

data class OnsetDetection(
    val onsets: List<Onset>,
    val rawCount: Int,
    val removedLowLevel: Int,
    val removedDuplicates: Int,
)

/**
 * Detector de golpes por flujo espectral (port de Practica/src/vibrotactil/percussion.py,
 * que usa librosa 0.11 onset_strength + onset_detect):
 *
 * 1. Espectrograma mel: STFT 2048 / hop configurable, Hann periódica, relleno con ceros
 *    (center=True), potencia, 128 bandas mel Slaney (0 - sr/2), dB con top_db = 80.
 * 2. Novedad: media entre bandas de max(0, S[t] - S[t-1]), desplazada lag + n_fft/(2 hop)
 *    frames (convención center de librosa), normalizada a [0, 1].
 * 3. peak_pick de librosa: pre_max 30 ms, post_max 1 frame, promedio ±100 ms, delta.
 * 4. Filtro de nivel (relativo y absoluto) y eliminación de duplicados por intervalo mínimo.
 *
 * Sin backtrack: medido con golpes sintéticos, el backtrack adelantaba los golpes hasta
 * 23 ms; sin él el error queda en +1,7..+7,4 ms (< 2 hop / sr).
 */
class OnsetDetector(private val config: PercussionConfig) {

    fun detect(audio: FloatSignal, sampleRate: Int): OnsetDetection {
        val hop = config.hopLength
        val n = audio.size
        var anyNonZero = false
        for (i in 0 until n) if (audio[i] != 0f) { anyNonZero = true; break }
        if (n == 0 || !anyNonZero) return OnsetDetection(emptyList(), 0, 0, 0)

        val novelty = onsetStrength(audio, sampleRate, hop)
        val frames = peakPick(novelty, sampleRate, hop)
        if (frames.isEmpty()) return OnsetDetection(emptyList(), 0, 0, 0)

        val rms = rms(audio, frameLength = 4 * hop, hop = hop)
        val reference = rms.maxOrNull()!!.coerceAtLeast(1e-12)
        val window = max(1, (0.03 * sampleRate / hop).roundToInt())
        val kept = ArrayList<Pair<Int, Double>>()
        for (f in frames) {
            var level = 0.0
            for (j in f until minOf(f + window, rms.size)) level = max(level, rms[j])
            val relDb = 20 * log10(max(level, 1e-12) / reference)
            val absDb = 20 * log10(max(level, 1e-12))
            if (relDb >= config.minLevelDb && absDb >= config.minAbsoluteDbfs) kept.add(f to level)
        }
        val onsets = ArrayList<Onset>()
        var lastT = Double.NEGATIVE_INFINITY
        for ((f, level) in kept) {
            val t = f.toDouble() * hop / sampleRate
            if (t - lastT < config.minIntervalS) continue
            onsets.add(Onset(t, level / reference))
            lastT = t
        }
        return OnsetDetection(onsets, frames.size, frames.size - kept.size, kept.size - onsets.size)
    }

    /** librosa.onset.onset_strength(y, sr, hop_length) con sus valores por defecto. */
    internal fun onsetStrength(audio: FloatSignal, sr: Int, hop: Int): DoubleArray {
        val nFft = N_FFT
        val nFrames = 1 + audio.size / hop
        val nBins = nFft / 2 + 1
        val mel = melFilterBank(sr, nFft, N_MELS)
        val fft = Fft(nFft)
        val window = hannPeriodic(nFft)
        val re = DoubleArray(nFft)
        val im = DoubleArray(nFft)
        val power = DoubleArray(nBins)
        val sDb = Array(nFrames) { DoubleArray(N_MELS) }
        var globalMax = Double.NEGATIVE_INFINITY
        for (t in 0 until nFrames) {
            val start = t * hop - nFft / 2 // center=True, pad_mode='constant'
            for (k in 0 until nFft) {
                val idx = start + k
                re[k] = if (idx in 0 until audio.size) audio[idx] * window[k] else 0.0
                im[k] = 0.0
            }
            fft.forward(re, im)
            for (b in 0 until nBins) power[b] = re[b] * re[b] + im[b] * im[b]
            for (m in 0 until N_MELS) {
                var s = 0.0
                val row = mel[m]
                for (b in row.first until row.first + row.second.size) s += row.second[b - row.first] * power[b]
                val db = 10 * log10(max(AMIN, s))
                sDb[t][m] = db
                if (db > globalMax) globalMax = db
            }
        }
        val floor = globalMax - TOP_DB
        for (row in sDb) for (m in row.indices) if (row[m] < floor) row[m] = floor

        val padFrames = 1 + nFft / (2 * hop) // lag + n_fft // (2 hop)
        val env = DoubleArray(nFrames)
        for (k in padFrames until nFrames) {
            val t = k - padFrames + 1
            var s = 0.0
            for (m in 0 until N_MELS) s += max(0.0, sDb[t][m] - sDb[t - 1][m])
            env[k] = s / N_MELS
        }
        return env
    }

    /** librosa.onset.onset_detect(normalize=True) + util.peak_pick, con wait = 0. */
    internal fun peakPick(envelope: DoubleArray, sr: Int, hop: Int): List<Int> {
        val n = envelope.size
        val min = envelope.minOrNull() ?: return emptyList()
        val x = DoubleArray(n) { envelope[it] - min }
        val max = x.maxOrNull()!!
        if (max <= 0.0) return emptyList()
        for (i in 0 until n) x[i] /= max + TINY_F32
        val preMax = ceil(floorDiv(0.03 * sr, hop)).toInt()
        val postMax = ceil(floorDiv(0.00 * sr, hop) + 1).toInt()
        val preAvg = ceil(floorDiv(0.10 * sr, hop)).toInt()
        val postAvg = ceil(floorDiv(0.10 * sr, hop) + 1).toInt()
        val wait = 0
        val delta = config.onsetDelta
        val peaks = ArrayList<Int>()
        var i: Int
        val first = x[0] >= maxRange(x, 0, minOf(postMax, n)) && x[0] >= meanRange(x, 0, minOf(postAvg, n)) + delta
        if (first) { peaks.add(0); i = wait + 1 } else i = 1
        while (i < n) {
            if (x[i] != maxRange(x, max(0, i - preMax), minOf(i + postMax, n))) { i++; continue }
            if (x[i] < meanRange(x, max(0, i - preAvg), minOf(i + postAvg, n)) + delta) { i++; continue }
            peaks.add(i)
            i += wait + 1
        }
        return peaks
    }

    /** librosa.feature.rms(frame_length, hop_length, center=True, pad_mode='constant'). */
    internal fun rms(audio: FloatSignal, frameLength: Int, hop: Int): DoubleArray {
        val nFrames = 1 + audio.size / hop
        return DoubleArray(nFrames) { t ->
            val start = t * hop - frameLength / 2
            var s = 0.0
            for (k in 0 until frameLength) {
                val idx = start + k
                if (idx in 0 until audio.size) { val v = audio[idx].toDouble(); s += v * v }
            }
            sqrt(s / frameLength)
        }
    }

    companion object {
        const val N_FFT = 2048
        const val N_MELS = 128
        private const val AMIN = 1e-10
        private const val TOP_DB = 80.0
        private const val TINY_F32 = 1.17549435e-38

        private fun floorDiv(a: Double, b: Int): Double = kotlin.math.floor(a / b)
        private fun maxRange(x: DoubleArray, from: Int, to: Int): Double {
            var m = Double.NEGATIVE_INFINITY
            for (i in from until to) if (x[i] > m) m = x[i]
            return m
        }
        private fun meanRange(x: DoubleArray, from: Int, to: Int): Double {
            var s = 0.0
            for (i in from until to) s += x[i]
            return s / (to - from)
        }

        /** librosa.filters.mel(htk=False, norm='slaney'); filas dispersas (primer bin, pesos). */
        internal fun melFilterBank(sr: Int, nFft: Int, nMels: Int): Array<Pair<Int, DoubleArray>> {
            val nBins = nFft / 2 + 1
            val fftFreqs = DoubleArray(nBins) { it * sr.toDouble() / nFft }
            val melMin = hzToMel(0.0)
            val melMax = hzToMel(sr / 2.0)
            val melF = DoubleArray(nMels + 2) { melToHz(melMin + (melMax - melMin) * it / (nMels + 1)) }
            return Array(nMels) { i ->
                val lowerDen = melF[i + 1] - melF[i]
                val upperDen = melF[i + 2] - melF[i + 1]
                val enorm = 2.0 / (melF[i + 2] - melF[i])
                val w = DoubleArray(nBins) { b ->
                    val lower = (fftFreqs[b] - melF[i]) / lowerDen
                    val upper = (melF[i + 2] - fftFreqs[b]) / upperDen
                    max(0.0, minOf(lower, upper)) * enorm
                }
                val first = w.indexOfFirst { it > 0 }.let { if (it < 0) 0 else it }
                val last = w.indexOfLast { it > 0 }.let { if (it < 0) 0 else it }
                first to w.copyOfRange(first, last + 1)
            }
        }

        // Escala mel de Slaney (librosa htk=False): lineal hasta 1 kHz y logarítmica después.
        private const val F_SP = 200.0 / 3
        private const val MIN_LOG_HZ = 1000.0
        private const val MIN_LOG_MEL = MIN_LOG_HZ / F_SP
        private val LOGSTEP = ln(6.4) / 27.0

        internal fun hzToMel(hz: Double): Double =
            if (hz >= MIN_LOG_HZ) MIN_LOG_MEL + ln(hz / MIN_LOG_HZ) / LOGSTEP else hz / F_SP

        internal fun melToHz(mel: Double): Double =
            if (mel >= MIN_LOG_MEL) MIN_LOG_HZ * exp(LOGSTEP * (mel - MIN_LOG_MEL)) else F_SP * mel
    }
}
