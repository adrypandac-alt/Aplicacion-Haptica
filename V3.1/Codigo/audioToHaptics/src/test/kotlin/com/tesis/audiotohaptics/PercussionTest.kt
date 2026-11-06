package com.tesis.audiotohaptics

import com.tesis.audiotohaptics.audio.WavReader
import com.tesis.audiotohaptics.config.PercussionConfig
import com.tesis.audiotohaptics.percussion.OnsetDetector
import com.tesis.audiotohaptics.storage.ArrayFloatSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class PercussionTest {
    private val cfg = PercussionConfig()
    private val detector = OnsetDetector(cfg)

    @Test
    fun knownHitsDetectedWithinFrameBound() {
        val rng = java.util.Random(1)
        var t = 0.3
        val hits = List(40) { t += 0.15 + 0.45 * rng.nextDouble(); t }
        val audio = percussiveAudio(hits, hits.last() + 1.0)
        val d = detector.detect(ArrayFloatSignal(audio), 44100)
        assertEquals(hits.size, d.onsets.size)
        val bound = 2.0 * cfg.hopLength / 44100 // rejilla de tramas + retardo de una trama
        val errors = d.onsets.zip(hits).map { (o, h) -> o.timeS - h }
        assertTrue("error máx ${errors.maxOf { abs(it) }}", errors.all { abs(it) <= bound })
        assertTrue(errors.min() >= -0.5 * cfg.hopLength / 44100)
    }

    @Test
    fun duplicatesWithinMinIntervalAreRemoved() {
        val d = detector.detect(ArrayFloatSignal(percussiveAudio(listOf(0.5, 0.53, 1.2), 2.0)), 44100)
        assertEquals(2, d.onsets.size)
        assertTrue(d.removedDuplicates >= 1)
    }

    @Test
    fun silenceAndNoiseFloorProduceNoEvents() {
        assertEquals(0, detector.detect(ArrayFloatSignal(FloatArray(44100)), 44100).onsets.size)
        val noise = detector.detect(ArrayFloatSignal(percussiveAudio(emptyList(), 2.0)), 44100)
        assertTrue(noise.rawCount > 0)
        assertEquals(0, noise.onsets.size)
    }

    /**
     * Comparación con el prototipo Python (librosa) sobre la batería real de la canción 2:
     * Practica/results/vibrotactil/cancion_prueba_2/percussion_events.csv (397 golpes).
     */
    @Test
    fun matchesPythonPrototypeOnRealDrums() {
        val drums = File(TestPaths.practica, "results/separacion/htdemucs_6s/cancion_prueba_2/drums.wav")
        val csv = File(TestPaths.practica, "results/vibrotactil/cancion_prueba_2/percussion_events.csv")
        TestPaths.require(drums, csv)
        val (info, ch) = WavReader.read(drums)
        val mono = FloatArray(info.frames) { i -> ch.map { it[i] }.average().toFloat() }
        val d = detector.detect(ArrayFloatSignal(mono), info.sampleRate)
        val expected = csv.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",")[0].toDouble() }
        val got = d.onsets.map { it.timeS }
        val matched = expected.count { e -> got.any { abs(it - e) < 1e-6 } }
        println("Percusión cancion_prueba_2: Python=${expected.size} Kotlin=${got.size} idénticos=$matched " +
            "(brutos=${d.rawCount}, duplicados=${d.removedDuplicates}, nivel=${d.removedLowLevel})")
        File(TestPaths.results, "kotlin_percusion_vs_python.txt").apply { parentFile.mkdirs() }
            .writeText("python=${expected.size}\nkotlin=${got.size}\nidenticos=$matched\n")
        assertEquals(expected.size, got.size)
        assertEquals(expected.size, matched)
    }
}
