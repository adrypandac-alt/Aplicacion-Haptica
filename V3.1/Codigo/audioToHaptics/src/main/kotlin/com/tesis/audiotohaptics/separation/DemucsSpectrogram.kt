package com.tesis.audiotohaptics.separation

import com.tesis.audiotohaptics.dsp.Fft
import com.tesis.audiotohaptics.dsp.hannPeriodic
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * STFT/iSTFT de HTDemucs fuera del grafo ONNX (HTDemucs._spec, _magnitude, _mask, _ispec).
 * Port de tools/model_export/reference_demucs.py, validado contra PyTorch (SDR 48-98 dB).
 */
internal class DemucsSpectrogram {
    private val fft = Fft(NFFT)
    private val window = hannPeriodic(NFFT)
    private val norm = 1.0 / sqrt(NFFT.toDouble())
    private val re = DoubleArray(NFFT)
    private val im = DoubleArray(NFFT)

    /**
     * Entrada del modelo `spec` [1, 4, 2048, 336] (canales = canal*2 + {re, im}).
     * @param mix segmento estéreo normalizado, [2 * SEGMENT] (canal-mayor).
     */
    fun specInput(mix: FloatArray): FloatArray {
        val out = FloatArray(4 * FREQS * FRAMES)
        val pad = HOP / 2 * 3
        val padded = DoubleArray(PADDED_LEN)
        for (c in 0 until 2) {
            // Relleno reflect de _spec y luego relleno center (reflect) de torch.stft.
            val offset = c * SEGMENT
            val first = DoubleArray(SEGMENT + pad + (pad + FRAMES * HOP - SEGMENT))
            for (i in first.indices) first[i] = mix[offset + reflect(i - pad, SEGMENT)].toDouble()
            for (i in padded.indices) padded[i] = first[reflect(i - NFFT / 2, first.size)]
            for (t in 0 until FRAMES) {
                val start = (t + 2) * HOP // se descartan los 2 primeros frames
                for (n in 0 until NFFT) {
                    re[n] = padded[start + n] * window[n]
                    im[n] = 0.0
                }
                fft.forward(re, im)
                for (f in 0 until FREQS) { // se descarta el bin de Nyquist
                    out[((c * 2) * FREQS + f) * FRAMES + t] = (re[f] * norm).toFloat()
                    out[((c * 2 + 1) * FREQS + f) * FRAMES + t] = (im[f] * norm).toFloat()
                }
            }
        }
        return out
    }

    /**
     * iSTFT de la salida `spec_out` [1, 6, 4, 2048, 336] para una fuente y un canal.
     * Devuelve SEGMENT muestras (antes de sumar la rama temporal).
     */
    fun inverse(specOut: FloatBuffer, source: Int, channel: Int): DoubleArray {
        // _ispec: rellena 1 bin (Nyquist = 0) y 2 frames de ceros a cada lado -> 340 frames.
        val totalFrames = FRAMES + 4
        val length = NFFT + HOP * (totalFrames - 1)
        val acc = DoubleArray(length)
        val wsum = DoubleArray(length)
        val baseRe = ((source * 4) + channel * 2) * FREQS * FRAMES
        val baseIm = baseRe + FREQS * FRAMES
        for (frame in 0 until totalFrames) {
            val t = frame - 2
            if (t in 0 until FRAMES) {
                // Espectro hermítico completo para la FFT compleja inversa.
                for (f in 0 until FREQS) {
                    re[f] = specOut.get(baseRe + f * FRAMES + t).toDouble()
                    im[f] = specOut.get(baseIm + f * FRAMES + t).toDouble()
                }
                re[FREQS] = 0.0; im[FREQS] = 0.0
                im[0] = 0.0 // irfft ignora la parte imaginaria de DC y Nyquist
                for (f in 1 until FREQS) {
                    re[NFFT - f] = re[f]
                    im[NFFT - f] = -im[f]
                }
                fft.inverseUnscaled(re, im)
                val start = frame * HOP
                // irfft (1/N) * sqrt(N) (normalized=True) = 1/sqrt(N)
                for (n in 0 until NFFT) acc[start + n] += re[n] * norm * window[n]
            }
            val start = frame * HOP
            for (n in 0 until NFFT) wsum[start + n] += window[n] * window[n]
        }
        val pad = HOP / 2 * 3
        val out = DoubleArray(SEGMENT)
        val origin = NFFT / 2 + pad // recorte center de istft + recorte pad de _ispec
        for (i in 0 until SEGMENT) {
            val w = wsum[origin + i]
            out[i] = if (w > 1e-11) acc[origin + i] / w else acc[origin + i]
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val NFFT = 4096
        const val HOP = 1024
        const val SEGMENT = 343980 // int(7.8 * 44100), longitud de entrenamiento
        const val FRAMES = 336 // ceil(SEGMENT / HOP)
        const val FREQS = NFFT / 2
        const val SOURCES = 6
        private const val PADDED_LEN = FRAMES * HOP + 3 * HOP + NFFT

        /** Índice reflejado tipo numpy/torch 'reflect' (sin repetir el borde). */
        fun reflect(i: Int, n: Int): Int {
            var j = i
            val period = 2 * (n - 1)
            if (j < 0 || j >= n) {
                j = Math.floorMod(j, period)
                if (j >= n) j = period - j
            }
            return j
        }
    }
}
