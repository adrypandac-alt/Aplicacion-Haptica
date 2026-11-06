package com.tesis.audiotohaptics

import com.tesis.audiotohaptics.audio.Pcm16MonoWavWriter
import com.tesis.audiotohaptics.audio.WavReader
import com.tesis.audiotohaptics.dsp.Fft
import com.tesis.audiotohaptics.dsp.Resampler
import com.tesis.audiotohaptics.separation.DemucsSpectrogram
import com.tesis.audiotohaptics.storage.ArrayFloatSignal
import com.tesis.audiotohaptics.storage.MappedFloatArray
import com.tesis.audiotohaptics.transcription.MidiWriter
import com.tesis.audiotohaptics.transcription.NoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class DspTest {
    @Test
    fun fftMatchesNaiveDft() {
        val n = 64
        val rng = java.util.Random(1)
        val x = DoubleArray(n) { rng.nextGaussian() }
        val re = x.copyOf()
        val im = DoubleArray(n)
        Fft(n).forward(re, im)
        for (k in 0 until n) {
            var sr = 0.0
            var si = 0.0
            for (t in 0 until n) {
                sr += x[t] * cos(2 * PI * k * t / n)
                si -= x[t] * sin(2 * PI * k * t / n)
            }
            assertEquals(sr, re[k], 1e-9)
            assertEquals(si, im[k], 1e-9)
        }
        Fft(n).inverseUnscaled(re, im)
        for (t in 0 until n) assertEquals(x[t], re[t] / n, 1e-12)
    }

    @Test
    fun resamplerMatchesNumpyReference() {
        TestPaths.require(File(TestPaths.fixtures, "resample_in_48k.f32"))
        val x = readF32(File(TestPaths.fixtures, "resample_in_48k.f32"))
        for ((fin, fout, name) in listOf(Triple(48000, 44100, "resample_48k_44k.f32"), Triple(44100, 22050, "resample_44k_22k.f32"))) {
            val expected = readF32(File(TestPaths.fixtures, name))
            val out = FloatArray(Resampler.outputLength(x.size, fin, fout))
            Resampler.resample(ArrayFloatSignal(x), fin, fout, ArrayFloatSignal(out))
            assertEquals(expected.size, out.size)
            val maxDiff = expected.indices.maxOf { abs(expected[it] - out[it]) }
            // Mismo algoritmo en float64 en ambos lados: solo difiere el redondeo final a float32.
            assertTrue("diferencia máxima $maxDiff", maxDiff < 1e-5)
        }
    }

    @Test
    fun resamplerPreservesSineFrequency() {
        val fin = 48000
        val x = FloatArray(fin) { sin(2 * PI * 1000 * it / fin).toFloat() }
        val out = FloatArray(Resampler.outputLength(x.size, fin, 44100))
        Resampler.resample(ArrayFloatSignal(x), fin, 44100, ArrayFloatSignal(out))
        // Comparación con la senoide ideal en la zona central (lejos de los bordes del filtro).
        var maxErr = 0.0
        for (i in 2000 until out.size - 2000) maxErr = maxOf(maxErr, abs(out[i] - sin(2 * PI * 1000 * i / 44100.0)))
        assertTrue("error $maxErr", maxErr < 1e-3)
    }

    @Test
    fun reflectIndexMatchesNumpy() {
        // np.pad([0,1,2,3], 3, 'reflect') -> [3,2,1,0,1,2,3,2,1,0]
        val got = (-3 until 7).map { DemucsSpectrogram.reflect(it, 4) }
        assertEquals(listOf(3, 2, 1, 0, 1, 2, 3, 2, 1, 0), got)
    }

    @Test
    fun wavRoundTripAndMappedStorage() {
        val dir = TestPaths.tempDir("dsp")
        val f = File(dir, "x.wav")
        val samples = ShortArray(1000) { (it * 30).toShort() }
        Pcm16MonoWavWriter(f, 16000, samples.size).use { it.write(samples) }
        val (info, ch) = WavReader.read(f)
        assertEquals(16000, info.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(16, info.bitsPerSample)
        assertEquals(1000, info.frames)
        assertEquals(samples[999] / 32768f, ch[0][999], 0f)

        MappedFloatArray.create(dir, "m.f32", 10).use { m ->
            m[3] = 1.5f; m.add(3, 2f)
            assertEquals(3.5f, m[3], 0f)
            assertEquals(0f, m[9], 0f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWavIsRejected() {
        val f = File(TestPaths.tempDir("dsp_bad"), "bad.wav").apply { writeText("no es un wav") }
        WavReader.read(f)
    }

    @Test
    fun midiWriterUsesBasicPitchTiming() {
        val f = File(TestPaths.tempDir("midi"), "n.mid")
        MidiWriter.write(f, listOf(NoteEvent(1.0, 1.5, 60, 100), NoteEvent(1.25, 2.0, 64, 80)))
        val b = f.readBytes()
        assertEquals("MThd", String(b, 0, 4))
        assertEquals(MidiWriter.TICKS_PER_BEAT, ((b[12].toInt() and 0xFF) shl 8) or (b[13].toInt() and 0xFF))
        // 120 BPM, 220 ticks/negra -> 440 ticks/s
        assertEquals(listOf(440L to 60, 550L to 64), noteOns(b))
    }

    /** Parser SMF mínimo: (tick absoluto, nota) de los note_on con velocidad > 0. */
    private fun noteOns(b: ByteArray): List<Pair<Long, Int>> {
        val ons = ArrayList<Pair<Long, Int>>()
        var p = 14
        while (p + 8 <= b.size) {
            val len = ((b[p + 4].toInt() and 0xFF) shl 24) or ((b[p + 5].toInt() and 0xFF) shl 16) or
                ((b[p + 6].toInt() and 0xFF) shl 8) or (b[p + 7].toInt() and 0xFF)
            var q = p + 8
            val end = q + len
            var tick = 0L
            while (q < end) {
                var delta = 0L
                do { val c = b[q++].toInt() and 0xFF; delta = (delta shl 7) or (c and 0x7F).toLong() } while (c and 0x80 != 0)
                tick += delta
                val status = b[q].toInt() and 0xFF
                when {
                    status == 0xFF -> { q += 2; q += (b[q].toInt() and 0xFF) + 1 }
                    status and 0xF0 == 0xC0 -> q += 2
                    status and 0xF0 == 0x90 -> {
                        if (b[q + 2] > 0) ons.add(tick to (b[q + 1].toInt() and 0xFF))
                        q += 3
                    }
                    else -> q += 3
                }
            }
            p = end
        }
        return ons
    }
}
