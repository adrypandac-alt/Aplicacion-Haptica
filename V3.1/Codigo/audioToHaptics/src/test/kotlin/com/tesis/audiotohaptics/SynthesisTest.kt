package com.tesis.audiotohaptics

import com.tesis.audiotohaptics.audio.WavReader
import com.tesis.audiotohaptics.config.EnvelopeConfig
import com.tesis.audiotohaptics.config.OverlapPolicy
import com.tesis.audiotohaptics.synthesis.HapticEvent
import com.tesis.audiotohaptics.synthesis.SignalSynthesizer
import com.tesis.audiotohaptics.validation.SignalValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pruebas digitales de la síntesis (equivalentes a Practica/tests/test_synthesis.py). */
class SynthesisTest {
    private val sr = 8000
    private val env = EnvelopeConfig()
    private val dir = TestPaths.tempDir("synthesis")

    private fun render(events: List<HapticEvent>, durationS: Double, carrier: Double = 150.0,
                       envelope: EnvelopeConfig = env, amp: Double = 0.9, name: String = "x.wav"): File {
        val f = File(dir, name)
        SignalSynthesizer(envelope, sr).synthesize(events, (durationS * sr).roundToInt(), carrier, amp, f)
        return f
    }

    @Test
    fun envelopeSupportMatchesNoteExactly() {
        val e = SignalSynthesizer(env, sr).envelopeValues(listOf(HapticEvent(0.5, 1.25)), 2 * sr, 0, 2 * sr)
        val active = e.indices.filter { e[it] > 0 }
        assertEquals((0.5 * sr).roundToInt(), active.first())
        assertEquals((1.25 * sr).roundToInt(), active.last() + 1)
        assertEquals(1.0, e.max(), 1e-12)
    }

    @Test
    fun shortNoteRampsAreScaled() {
        val e = SignalSynthesizer(env, sr).envelopeValues(listOf(HapticEvent(0.1, 0.106)), sr / 5, 0, sr / 5)
        assertEquals((0.106 * sr).roundToInt() - (0.1 * sr).roundToInt(), e.count { it > 0 })
        assertTrue(e.max() < 1.0)
    }

    @Test
    fun transitionsAreSmooth() {
        val e = SignalSynthesizer(env, sr).envelopeValues(listOf(HapticEvent(0.2, 0.5)), sr, 0, sr)
        val attack = (env.attackS * sr).roundToInt()
        val maxJump = (1 until e.size).maxOf { abs(e[it] - e[it - 1]) }
        assertTrue("salto $maxJump", maxJump < 2.0 / attack)
    }

    @Test
    fun velocityMapping() {
        val cfg = env.copy(mapVelocity = true, velocityMinGain = 0.2)
        val e = SignalSynthesizer(cfg, sr).envelopeValues(
            listOf(HapticEvent(0.1, 0.5, 127), HapticEvent(0.6, 1.0, 0)), (1.2 * sr).toInt(), 0, (1.2 * sr).toInt())
        assertEquals(1.0, e[(0.3 * sr).toInt()], 1e-12)
        assertEquals(0.2, e[(0.8 * sr).toInt()], 1e-12)
    }

    @Test
    fun simultaneousEventsNeverClip() {
        val events = listOf(HapticEvent(0.1, 0.9), HapticEvent(0.2, 0.6), HapticEvent(0.3, 0.5))
        for (policy in OverlapPolicy.entries) {
            for (amp in listOf(0.9, 1.0)) {
                val f = render(events, 1.0, envelope = env.copy(overlapPolicy = policy), amp = amp, name = "p_$policy.wav")
                val r = SignalValidator.validate(f, events, 150.0, amp, 0.01)
                assertTrue(r.peakPcm <= (amp * 32767).roundToInt())
                if (amp < 1.0) assertEquals(0, r.clippedSamples)
            }
        }
    }

    @Test
    fun outputIsMonoPcm16WithConfiguredRate() {
        val (info, _) = WavReader.read(render(listOf(HapticEvent(0.1, 0.2)), 0.5))
        assertEquals(1, info.channels)
        assertEquals(16, info.bitsPerSample)
        assertEquals(1, info.formatTag)
        assertEquals(sr, info.sampleRate)
        assertEquals(4000, info.frames)
    }

    @Test
    fun carrierFrequencyMatchesConfigAndIgnoresPitch() {
        for (carrier in listOf(150.0, 200.0, 250.0)) {
            val f = render(listOf(HapticEvent(0.0, 2.0)), 2.0, carrier)
            val r = SignalValidator.validate(f, listOf(HapticEvent(0.0, 2.0)), carrier, 0.9, 0.01)
            // Resolución: 2^17 muestras de análisis con zero-padding x4 e interpolación parabólica.
            assertEquals(carrier, r.measuredCarrierHz!!, 0.1)
        }
        // Notas de alturas MIDI muy distintas producen la misma portadora.
        val (_, ch) = WavReader.read(render(listOf(HapticEvent(0.0, 1.0, 40), HapticEvent(1.0, 2.0, 120)), 2.0))
        val low = SignalValidator.estimateCarrier(ch[0].copyOfRange(0, sr).map { it.toDouble() }.toDoubleArray(), sr)!!
        val high = SignalValidator.estimateCarrier(ch[0].copyOfRange(sr, 2 * sr).map { it.toDouble() }.toDoubleArray(), sr)!!
        assertEquals(150.0, low, 0.2)
        assertEquals(150.0, high, 0.2)
    }

    @Test
    fun timingErrorsWithinMeasurementBound() {
        val cases = listOf(
            listOf(HapticEvent(0.5, 1.5)),
            List(5) { HapticEvent(0.2 + 0.4 * it, 0.4 + 0.4 * it) },
            listOf(HapticEvent(0.2, 1.0), HapticEvent(0.5, 1.4), HapticEvent(0.6, 0.8)),
        )
        val expectedSegments = listOf(1, 5, 1)
        for ((events, nSeg) in cases.zip(expectedSegments)) {
            val f = render(events, 3.0)
            val r = SignalValidator.validate(f, events, 150.0, 0.9, 0.01)
            assertEquals(nSeg, r.expectedSegments)
            assertEquals(nSeg, r.detectedSegments)
            assertEquals(nSeg, r.matchedSegments)
            assertTrue(r.startError!!.maxAbsMs <= r.measurementBoundMs)
            assertTrue(r.endError!!.maxAbsMs <= r.measurementBoundMs)
        }
    }

    @Test
    fun threeOutputsShareTimeReference() {
        val total = (4.0 * sr).roundToInt()
        val synth = SignalSynthesizer(env, sr)
        val files = listOf(150.0, 200.0, 250.0).mapIndexed { i, c ->
            File(dir, "a$i.wav").also { synth.synthesize(if (i == 2) emptyList() else listOf(HapticEvent(1.0, 2.0)), total, c, 0.9, it) }
        }
        assertEquals(setOf(total), files.map { WavReader.info(it).frames }.toSet())
        // Una fuente sin eventos produce silencio de la duración completa.
        assertTrue(WavReader.read(files[2]).second[0].all { it == 0f })
    }
}
