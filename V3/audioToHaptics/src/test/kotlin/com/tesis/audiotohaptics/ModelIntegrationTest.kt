package com.tesis.audiotohaptics

import com.tesis.audiotohaptics.audio.WavReader
import com.tesis.audiotohaptics.dsp.Resampler
import com.tesis.audiotohaptics.separation.DemucsSeparator
import com.tesis.audiotohaptics.separation.DemucsSource
import com.tesis.audiotohaptics.storage.ArrayFloatSignal
import com.tesis.audiotohaptics.storage.MappedFloatArray
import com.tesis.audiotohaptics.transcription.BasicPitchTranscriber
import com.tesis.audiotohaptics.transcription.NoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Pruebas con los modelos ONNX reales ejecutados en el JVM (ONNX Runtime de escritorio,
 * misma API Java que onnxruntime-android). Validan los ports Kotlin contra las
 * referencias numpy y contra los resultados del prototipo Python.
 */
class ModelIntegrationTest {

    private fun readNotesCsv(f: File): List<NoteEvent> = f.readLines().drop(1).filter { it.isNotBlank() }.map {
        val c = it.split(",")
        NoteEvent(c[0].toDouble(), c[1].toDouble(), c[2].toInt(), c[3].toInt())
    }

    private fun monoOf(f: File): Pair<FloatArray, Int> {
        val (info, ch) = WavReader.read(f)
        return FloatArray(info.frames) { i -> ch.map { it[i] }.average().toFloat() } to info.sampleRate
    }

    @Test
    fun basicPitchKotlinMatchesNumpyReferenceAndPrototype() {
        TestPaths.require(TestPaths.basicPitchModel)
        // Integración: el ZIP no incluye los stems WAV ni las referencias numpy.
        val referenciasPresentes = listOf("cancion_prueba", "cancion_prueba_2").any { song ->
            listOf("vocals", "guitar").any { stem ->
                File(TestPaths.practica, "results/separacion/htdemucs_6s/$song/$stem.wav").isFile &&
                    File(TestPaths.fixtures, "bp_${stem}_$song.csv").isFile
            }
        }
        org.junit.Assume.assumeTrue("Faltan audios y referencias externas de Basic Pitch", referenciasPresentes)
        val report = StringBuilder()
        BasicPitchTranscriber(TestPaths.basicPitchModel).use { bp ->
            for (song in listOf("cancion_prueba", "cancion_prueba_2")) for (stem in listOf("vocals", "guitar")) {
                val wav = File(TestPaths.practica, "results/separacion/htdemucs_6s/$song/$stem.wav")
                val fixture = File(TestPaths.fixtures, "bp_${stem}_$song.csv")
                val proto = File(TestPaths.practica, "results/midi/$song/$stem/${stem}_basic_pitch.csv")
                if (!wav.exists() || !fixture.exists()) continue
                val (mono, sr) = monoOf(wav)
                val a22 = FloatArray(Resampler.outputLength(mono.size, sr, BasicPitchTranscriber.SAMPLE_RATE))
                Resampler.resample(ArrayFloatSignal(mono), sr, BasicPitchTranscriber.SAMPLE_RATE, ArrayFloatSignal(a22))
                val t0 = System.nanoTime()
                val notes = bp.transcribe(a22).sortedWith(compareBy({ it.startS }, { it.endS }, { it.pitch }))
                val secs = (System.nanoTime() - t0) / 1e9
                // 1) Idéntico a la referencia numpy (mismo remuestreador y posprocesamiento).
                val ref = readNotesCsv(fixture).sortedWith(compareBy({ it.startS }, { it.endS }, { it.pitch }))
                assertEquals("$song/$stem: número de notas", ref.size, notes.size)
                for ((a, b) in notes.zip(ref)) {
                    assertEquals(b.pitch, a.pitch)
                    assertEquals(b.startS, a.startS, 1e-6)
                    assertEquals(b.endS, a.endS, 1e-6)
                }
                // 2) Coherencia con el CSV del prototipo (Basic Pitch CLI, backend TensorFlow).
                val m = NoteMatch.match(readNotesCsv(proto), notes)
                report.append("$song/$stem: kotlin=${notes.size} prototipo=${m.refCount} F1=${"%.4f".format(m.f1)} " +
                    "err_inicio_max=${"%.2f".format(m.maxOnsetErrMs)} ms tiempo=${"%.2f".format(secs)} s\n")
            }
        }
        println(report)
        File(TestPaths.results, "kotlin_basic_pitch_vs_python.txt").apply { parentFile.mkdirs() }.writeText(report.toString())
        assertTrue(report.isNotEmpty())
    }

    @Test
    fun demucsKotlinMatchesNumpyReference() {
        val mix = File(TestPaths.fixtures, "cancion_prueba_mix44k.wav")
        val refDir = File(TestPaths.models, "reference_stems/cancion_prueba")
        TestPaths.require(TestPaths.demucsModel, mix, refDir)
        val (info, ch) = WavReader.read(mix)
        val dir = TestPaths.tempDir("demucs_it")
        val wanted = listOf(DemucsSource.VOCALS, DemucsSource.GUITAR, DemucsSource.DRUMS)
        val outs = wanted.associateWith { MappedFloatArray.create(dir, "${it.name}.f32", info.frames) }
        val memory = MemorySampler().apply { start() }
        val t0 = System.nanoTime()
        DemucsSeparator(TestPaths.demucsModel, threads = 4).use { sep ->
            MappedFloatArray.create(dir, "w.f32", info.frames).use { w ->
                sep.separate(ArrayFloatSignal(ch[0]), ArrayFloatSignal(ch[1]), outs, w)
            }
        }
        val secs = (System.nanoTime() - t0) / 1e9
        memory.stop()
        val report = StringBuilder("audio=${"%.2f".format(info.frames / 44100.0)} s tiempo=${"%.1f".format(secs)} s " +
            "pico_memoria_privada_proceso=${memory.peakMb} MB\n")
        for (s in wanted) {
            val (refMono, _) = monoOf(File(refDir, "${s.name.lowercase()}.wav"))
            val got = FloatArray(info.frames) { outs.getValue(s)[it] }
            val sdr = sdrDb(refMono, got)
            report.append("${s.name}: SDR Kotlin vs referencia numpy = ${"%.1f".format(sdr)} dB\n")
            // Ambos implementan el mismo algoritmo; las diferencias son de redondeo float32.
            assertTrue("${s.name} SDR $sdr", sdr > 40)
        }
        outs.values.forEach { it.close() }
        println(report)
        File(TestPaths.results, "kotlin_demucs_vs_numpy.txt").writeText(report.toString())
    }
}

/** Emparejamiento de notas: misma altura y |inicio| <= 50 ms (como validate_basic_pitch.py). */
object NoteMatch {
    data class Result(val refCount: Int, val estCount: Int, val matched: Int, val f1: Double, val maxOnsetErrMs: Double)

    fun match(ref: List<NoteEvent>, est: List<NoteEvent>, tol: Double = 0.05): Result {
        val used = BooleanArray(ref.size)
        var hits = 0
        var maxErr = 0.0
        for (e in est.sortedBy { it.startS }) {
            var best = -1
            var bestD = tol + 1e-9
            for (j in ref.indices) {
                if (used[j] || ref[j].pitch != e.pitch) continue
                val d = abs(ref[j].startS - e.startS)
                if (d <= bestD) { best = j; bestD = d }
            }
            if (best >= 0) { used[best] = true; hits++; maxErr = maxOf(maxErr, bestD * 1000) }
        }
        val p = hits.toDouble() / maxOf(est.size, 1)
        val r = hits.toDouble() / maxOf(ref.size, 1)
        return Result(ref.size, est.size, hits, if (p + r > 0) 2 * p * r / (p + r) else 0.0, maxErr)
    }
}

/**
 * Muestrea la memoria privada del proceso (incluye la memoria nativa de ONNX Runtime) cada
 * 50 ms vía com.sun.management (por reflexión: no está en android.jar). Es una medida de PC,
 * no del teléfono.
 */
class MemorySampler {
    @Volatile private var running = false
    @Volatile var peakBytes = 0L
    private var thread: Thread? = null
    val peakMb: Long get() = peakBytes / (1024 * 1024)

    private val read: () -> Long = makeReader()

    private fun makeReader(): () -> Long {
        return try {
            val mf = Class.forName("java.lang.management.ManagementFactory")
            val bean = mf.getMethod("getOperatingSystemMXBean").invoke(null)
            val method = Class.forName("com.sun.management.OperatingSystemMXBean")
                .getMethod("getCommittedVirtualMemorySize")
            val reader: () -> Long = { method.invoke(bean) as Long }
            reader
        } catch (_: Throwable) {
            val unavailable: () -> Long = { -1L }
            unavailable
        }
    }

    fun start() {
        running = true
        thread = Thread { while (running) { peakBytes = maxOf(peakBytes, read()); Thread.sleep(50) } }.apply { isDaemon = true; start() }
    }

    fun stop() { running = false; thread?.join() }
}
