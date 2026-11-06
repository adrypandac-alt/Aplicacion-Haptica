package com.tesis.audiotohaptics

import com.tesis.audiotohaptics.audio.WavReader
import com.tesis.audiotohaptics.config.Actuator
import com.tesis.audiotohaptics.config.HapticsConfig
import com.tesis.audiotohaptics.pipeline.DecodedAudio
import com.tesis.audiotohaptics.pipeline.HapticsPipeline
import com.tesis.audiotohaptics.pipeline.ModelFiles
import com.tesis.audiotohaptics.storage.ArrayFloatSignal
import com.tesis.audiotohaptics.transcription.NoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Pipeline completo en el JVM con los modelos reales: audio decodificado a su frecuencia
 * nativa (48 kHz, como lo entrega MediaCodec) -> 3 WAV + MIDI + manifest.json.
 *
 * Compara con el prototipo Python (Demucs CLI shifts=1 + Basic Pitch CLI + librosa).
 * Las diferencias de eventos provienen sobre todo de la separación (shifts aleatorio en
 * el CLI frente a shifts=0 aquí). Se REPORTAN; las aserciones son solo invariantes.
 */
class PipelineIntegrationTest {

    @Test
    fun fullPipelineCancionPrueba() = runSong("cancion_prueba", "cancion_prueba_decoded_native.wav")

    /** Canción de 4 min 26 s: se ejecuta con -PathLongTests=true. */
    @Test
    fun fullPipelineCancionPrueba2() {
        assumeTrue("Prueba larga desactivada (use -PathLongTests=true)", System.getProperty("ath.longTests") == "true")
        runSong("cancion_prueba_2", "cancion_prueba_2_decoded48k.wav")
    }

    private fun runSong(song: String, decoded: String) {
        val input = File(TestPaths.fixtures, decoded)
        TestPaths.require(TestPaths.demucsModel, TestPaths.basicPitchModel, input)
        val (info, ch) = WavReader.read(input)
        val audio = DecodedAudio(ch.map { ArrayFloatSignal(it) }, info.sampleRate, input.name)
        val config: HapticsConfig = HapticsConfig.testValues().copy(inferenceThreads = 4, exportStems = true)
        val out = TestPaths.tempDir("pipeline_$song")
        val stagesSeen = LinkedHashSet<Stage>()
        val memory = MemorySampler().apply { start() }
        val t0 = System.nanoTime()
        val result = HapticsPipeline(config, ModelFiles(TestPaths.demucsModel, TestPaths.basicPitchModel), File(out, "work"))
            .run(audio, out, onProgress = { stagesSeen += it.stage })
        val totalS = (System.nanoTime() - t0) / 1e9
        memory.stop()

        // Invariantes del contrato.
        val expectedSamples = Math.round(info.frames.toDouble() / info.sampleRate * config.sampleRateHz!!).toInt()
        assertEquals(expectedSamples, result.totalSamples)
        for ((a, o) in result.outputs) {
            val w = WavReader.info(o.wavFile)
            assertEquals("$a mono", 1, w.channels)
            assertEquals(16, w.bitsPerSample)
            assertEquals(config.sampleRateHz, w.sampleRate)
            assertEquals("$a misma duración", expectedSamples, w.frames)
            assertEquals(0, o.validation.clippedSamples)
            assertTrue(o.validation.peakPcm <= Math.round(config.maxAmplitude * 32767))
            if (o.eventCount > 0) {
                assertEquals(o.validation.expectedSegments, o.validation.matchedSegments)
                assertTrue(o.validation.startError!!.maxAbsMs <= o.validation.measurementBoundMs)
                assertEquals(o.carrierHz, o.validation.measuredCarrierHz!!, 0.5)
            }
        }
        assertTrue(result.manifestFile.isFile)
        assertTrue(stagesSeen.containsAll(listOf(Stage.SEPARATION, Stage.SYNTHESIS, Stage.EXPORT)))
        // Temporales borrados (en Windows un archivo mapeado no se puede borrar hasta liberar el mapeo).
        if (!System.getProperty("os.name").startsWith("Windows")) {
            assertTrue(File(out, "work").listFiles().orEmpty().none { it.extension == "f32" })
        }

        // Comparación con el prototipo Python.
        val report = StringBuilder()
        report.appendLine("{")
        report.appendLine("  \"song\": \"$song\", \"audio_s\": ${"%.2f".format(java.util.Locale.ROOT, result.durationS)}, \"input_rate\": ${info.sampleRate},")
        report.appendLine("  \"total_s\": ${"%.1f".format(java.util.Locale.ROOT, totalS)}, \"peak_process_memory_mb\": ${memory.peakMb},")
        report.appendLine("  \"stage_s\": {" + result.stageSeconds.entries.joinToString(", ") {
            "\"${it.key.name.lowercase()}\": ${"%.2f".format(java.util.Locale.ROOT, it.value)}" } + "},")
        for (a in listOf(Actuator.VOICE, Actuator.GUITAR)) {
            val stem = if (a == Actuator.VOICE) "vocals" else "guitar"
            val proto = File(TestPaths.practica, "results/midi/$song/$stem/${stem}_basic_pitch.csv")
            val mine = result.outputs.getValue(a).eventsFile
            if (!proto.exists()) continue
            val m = NoteMatch.match(readNotes(proto), readNotes(mine))
            report.appendLine("  \"${a.name.lowercase()}\": {\"kotlin_notes\": ${m.estCount}, \"python_notes\": ${m.refCount}, " +
                "\"matched\": ${m.matched}, \"f1\": ${"%.4f".format(java.util.Locale.ROOT, m.f1)}},")
        }
        val protoPerc = File(TestPaths.practica, "results/vibrotactil/$song/percussion_events.csv")
        if (protoPerc.exists()) {
            val ref = readOnsets(protoPerc)
            val est = readOnsets(result.outputs.getValue(Actuator.PERCUSSION).eventsFile)
            val pm = onsetMatch(ref, est, 0.05)
            report.appendLine("  \"percussion\": {\"kotlin_onsets\": ${est.size}, \"python_onsets\": ${ref.size}, " +
                "\"matched_50ms\": ${pm.first}, \"f1_50ms\": ${"%.4f".format(java.util.Locale.ROOT, pm.second)}, " +
                "\"median_abs_err_ms\": ${"%.2f".format(java.util.Locale.ROOT, pm.third)}},")
        }
        report.appendLine("  \"outputs\": {" + result.outputs.entries.joinToString(", ") { (a, o) ->
            "\"${a.name.lowercase()}\": {\"status\": \"${o.status}\", \"events\": ${o.eventCount}, " +
                "\"start_err_max_ms\": ${o.validation.startError?.maxAbsMs}, \"carrier_hz\": ${o.validation.measuredCarrierHz}}"
        } + "}")
        report.appendLine("}")
        println(report)
        File(TestPaths.results, "kotlin_pipeline_$song.json").writeText(report.toString())
    }

    private fun readNotes(f: File): List<NoteEvent> = f.readLines().drop(1).filter { it.isNotBlank() }.map {
        val c = it.split(","); NoteEvent(c[0].toDouble(), c[1].toDouble(), c[2].toInt(), c[3].toInt())
    }

    private fun readOnsets(f: File): List<Double> =
        f.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",")[0].toDouble() }

    /** (emparejados, F1, mediana |error| ms) con tolerancia [tol] s, emparejamiento voraz. */
    private fun onsetMatch(ref: List<Double>, est: List<Double>, tol: Double): Triple<Int, Double, Double> {
        val used = BooleanArray(ref.size)
        val errs = ArrayList<Double>()
        for (e in est) {
            var best = -1
            var bestD = tol
            for (j in ref.indices) if (!used[j] && abs(ref[j] - e) <= bestD) { best = j; bestD = abs(ref[j] - e) }
            if (best >= 0) { used[best] = true; errs.add(bestD * 1000) }
        }
        val p = errs.size.toDouble() / maxOf(est.size, 1)
        val r = errs.size.toDouble() / maxOf(ref.size, 1)
        val f1 = if (p + r > 0) 2 * p * r / (p + r) else if (ref.isEmpty() && est.isEmpty()) 1.0 else 0.0
        return Triple(errs.size, f1, errs.sorted().getOrElse(errs.size / 2) { 0.0 })
    }
}
