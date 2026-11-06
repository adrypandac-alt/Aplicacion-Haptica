package com.tesis.audiotohaptics.pipeline

import com.tesis.audiotohaptics.ActuatorOutput
import com.tesis.audiotohaptics.ProcessingProgress
import com.tesis.audiotohaptics.ProcessingResult
import com.tesis.audiotohaptics.SourceStatus
import com.tesis.audiotohaptics.Stage
import com.tesis.audiotohaptics.audio.Pcm16MonoWavWriter
import com.tesis.audiotohaptics.config.Actuator
import com.tesis.audiotohaptics.config.HapticsConfig
import com.tesis.audiotohaptics.config.HapticsException
import com.tesis.audiotohaptics.dsp.Resampler
import com.tesis.audiotohaptics.percussion.OnsetDetector
import com.tesis.audiotohaptics.separation.DemucsSeparator
import com.tesis.audiotohaptics.separation.DemucsSource
import com.tesis.audiotohaptics.separation.DemucsSpectrogram
import com.tesis.audiotohaptics.storage.ArrayFloatSignal
import com.tesis.audiotohaptics.storage.FloatSignal
import com.tesis.audiotohaptics.storage.MappedFloatArray
import com.tesis.audiotohaptics.synthesis.HapticEvent
import com.tesis.audiotohaptics.synthesis.SignalSynthesizer
import com.tesis.audiotohaptics.transcription.BasicPitchTranscriber
import com.tesis.audiotohaptics.transcription.MidiWriter
import com.tesis.audiotohaptics.transcription.NoteEvent
import com.tesis.audiotohaptics.validation.SignalValidator
import java.io.File
import kotlin.math.roundToInt

/** Audio decodificado: canales (1 o 2) en arreglos mapeados y su frecuencia de muestreo. */
class DecodedAudio(val channels: List<FloatSignal>, val sampleRate: Int, val sourceName: String) {
    val frames: Int get() = channels.first().size
    val durationS: Double get() = frames.toDouble() / sampleRate
}

/** Rutas locales de los modelos (ya instalados; nunca se descargan). */
data class ModelFiles(val demucs: File, val basicPitch: File)

/**
 * Orquestación del pipeline completo, independiente de Android (se prueba en JVM):
 *
 *   audio -> 44,1 kHz estéreo -> htdemucs_6s (voz, guitarra, batería)
 *         -> Basic Pitch (voz, guitarra) -> notas + MIDI
 *         -> detector de golpes (batería) -> pulsos
 *         -> 3 WAV mono de portadora fija, mismo número de muestras y mismo origen
 *         -> validación digital -> manifest.json
 *
 * La referencia temporal es la duración de la canción ORIGINAL (antes de remuestrear).
 */
class HapticsPipeline(
    private val config: HapticsConfig,
    private val models: ModelFiles,
    private val workDir: File,
) {
    fun run(
        audio: DecodedAudio,
        outputDir: File,
        onProgress: (ProcessingProgress) -> Unit = {},
        checkCancelled: () -> Unit = {},
    ): ProcessingResult {
        config.validate()
        for ((name, f) in listOf("htdemucs_6s" to models.demucs, "basic_pitch" to models.basicPitch)) {
            if (!f.isFile) throw HapticsException.ModelNotAvailable(name, "No existe el modelo local: $f")
        }
        outputDir.mkdirs()
        workDir.mkdirs()
        val warnings = ArrayList<String>()
        val timings = LinkedHashMap<Stage, Double>()
        val closeables = ArrayList<AutoCloseable>()
        fun progress(stage: Stage, fraction: Double, message: String = "") =
            onProgress(ProcessingProgress(stage, fraction.coerceIn(0.0, 1.0), overall(stage, fraction), message))

        fun <T> stage(stage: Stage, block: () -> T): T {
            checkCancelled()
            progress(stage, 0.0)
            val t0 = System.nanoTime()
            val result = try {
                block()
            } catch (e: HapticsException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw HapticsException.ProcessingFailed(stage.name, e)
            }
            timings[stage] = (System.nanoTime() - t0) / 1e9
            progress(stage, 1.0)
            return result
        }

        try {
            val n44 = Resampler.outputLength(audio.frames, audio.sampleRate, SR)
            // 1. Mezcla estéreo a 44,1 kHz (un archivo mono se duplica en ambos canales).
            val (left, right) = stage(Stage.RESAMPLING) {
                val l = MappedFloatArray.create(workDir, "mix_l.f32", n44).also { closeables += it }
                val r = MappedFloatArray.create(workDir, "mix_r.f32", n44).also { closeables += it }
                Resampler.resample(audio.channels[0], audio.sampleRate, SR, l)
                progress(Stage.RESAMPLING, 0.5)
                Resampler.resample(audio.channels.getOrElse(1) { audio.channels[0] }, audio.sampleRate, SR, r)
                l to r
            }

            // 2. Separación: solo se acumulan las tres fuentes necesarias, en mono.
            val wanted = listOf(DemucsSource.VOCALS, DemucsSource.GUITAR, DemucsSource.DRUMS)
            val stems = stage(Stage.SEPARATION) {
                val outs = wanted.associateWith {
                    MappedFloatArray.create(workDir, "${it.name.lowercase()}.f32", n44).also { a -> closeables += a }
                }
                val weights = MappedFloatArray.create(workDir, "weights.f32", n44).also { closeables += it }
                DemucsSeparator(models.demucs, config.inferenceThreads).use { sep ->
                    sep.separate(left, right, outs, weights) { k, total ->
                        checkCancelled()
                        progress(Stage.SEPARATION, k.toDouble() / total, "Segmento $k de $total")
                    }
                }
                outs
            }
            val stemFiles = LinkedHashMap<String, File>()
            if (config.exportStems) {
                for ((source, signal) in stems) {
                    val f = File(outputDir, "stem_${source.name.lowercase()}.wav")
                    writeMonoWav(signal, SR, f)
                    stemFiles[source.name.lowercase()] = f
                }
            }

            // 3. Transcripción de voz y guitarra.
            val notes = LinkedHashMap<Actuator, List<NoteEvent>>()
            BasicPitchTranscriber(models.basicPitch, config.inferenceThreads).use { bp ->
                for ((actuator, source, st) in listOf(
                    Triple(Actuator.VOICE, DemucsSource.VOCALS, Stage.TRANSCRIPTION_VOICE),
                    Triple(Actuator.GUITAR, DemucsSource.GUITAR, Stage.TRANSCRIPTION_GUITAR),
                )) {
                    notes[actuator] = stage(st) {
                        val stem = stems.getValue(source)
                        val n22 = Resampler.outputLength(stem.size, SR, BasicPitchTranscriber.SAMPLE_RATE)
                        val mono22 = FloatArray(n22)
                        Resampler.resample(stem, SR, BasicPitchTranscriber.SAMPLE_RATE, ArrayFloatSignal(mono22))
                        bp.transcribe(mono22) { w, total -> checkCancelled(); progress(st, w.toDouble() / total) }
                    }
                }
            }

            // 4. Golpes de percusión.
            val detection = stage(Stage.PERCUSSION) {
                OnsetDetector(config.percussion).detect(stems.getValue(DemucsSource.DRUMS), SR)
            }

            // 5. Eventos hápticos con la referencia temporal de la canción original.
            val duration = audio.durationS
            val events = LinkedHashMap<Actuator, List<HapticEvent>>()
            for (a in listOf(Actuator.VOICE, Actuator.GUITAR)) {
                val all = notes.getValue(a).map { HapticEvent(it.startS, it.endS, it.velocity) }
                val kept = all.filter { it.durationS >= config.envelope.minEventDurationS && it.durationS > 0 }
                if (kept.size < all.size) warnings += "${a.name}: ${all.size - kept.size} notas filtradas por duración"
                events[a] = kept
            }
            val times = detection.onsets.map { it.timeS }
            events[Actuator.PERCUSSION] = times.mapIndexedNotNull { i, t ->
                val end = minOf(t + config.percussion.pulseDurationS, times.getOrElse(i + 1) { Double.MAX_VALUE })
                if (end > t) HapticEvent(t, end, 127) else null
            }

            // 6. Síntesis de los tres WAV: mismo número de muestras para todos.
            val sr = config.sampleRateHz!!
            val totalSamples = (duration * sr).roundToInt()
            val synth = SignalSynthesizer(config.envelope, sr)
            val reports = stage(Stage.SYNTHESIS) {
                Actuator.entries.associateWith { a ->
                    val ac = config.actuators.getValue(a)
                    val r = synth.synthesize(events.getValue(a), totalSamples, ac.carrierHz!!, config.maxAmplitude,
                        File(outputDir, ac.outputFile))
                    if (r.truncatedEvents > 0) warnings += "${a.name}: ${r.truncatedEvents} eventos recortados a la duración"
                    progress(Stage.SYNTHESIS, (a.ordinal + 1) / 3.0)
                    r
                }
            }

            // 7. Validación digital de cada WAV escrito.
            val ramp = maxOf(config.envelope.attackS, config.envelope.releaseS)
            val validations = stage(Stage.VALIDATION) {
                Actuator.entries.associateWith { a ->
                    val ac = config.actuators.getValue(a)
                    SignalValidator.validate(File(outputDir, ac.outputFile), events.getValue(a), ac.carrierHz!!,
                        config.maxAmplitude, ramp)
                }
            }

            // 8. Exportación: MIDI, CSV de eventos y manifiesto.
            val result = stage(Stage.EXPORT) {
                val outputs = LinkedHashMap<Actuator, ActuatorOutput>()
                for (a in Actuator.entries) {
                    val ac = config.actuators.getValue(a)
                    val midi = notes[a]?.let { File(outputDir, "${a.name.lowercase()}_notes.mid").also { f -> MidiWriter.write(f, it) } }
                    val csv = File(outputDir, "${a.name.lowercase()}_events.csv")
                    if (a == Actuator.PERCUSSION) {
                        csv.writeText("onset_s,pulse_end_s,relative_level\n" + events.getValue(a).zip(detection.onsets)
                            .joinToString("") { (e, o) -> "%.6f,%.6f,%.4f\n".format(java.util.Locale.ROOT, e.startS, e.endS, o.relativeLevel) })
                    } else {
                        csv.writeText("start_time_s,end_time_s,pitch_midi,velocity\n" + notes.getValue(a)
                            .joinToString("") { "%.6f,%.6f,%d,%d\n".format(java.util.Locale.ROOT, it.startS, it.endS, it.pitch, it.velocity) })
                    }
                    outputs[a] = ActuatorOutput(
                        a, ac.actuatorId, ac.carrierHz!!,
                        if (events.getValue(a).isEmpty()) SourceStatus.NO_EVENTS else SourceStatus.OK,
                        File(outputDir, ac.outputFile), midi, csv, events.getValue(a).size, validations.getValue(a),
                    )
                }
                val manifestFile = File(outputDir, "manifest.json")
                ManifestWriter.write(
                    manifestFile, config, audio, duration, totalSamples, outputs, events, reports, detection, warnings, timings,
                )
                ProcessingResult(outputDir, manifestFile, outputs, duration, totalSamples, config.confirmed,
                    warnings, timings.toMap(), stemFiles)
            }
            return result
        } finally {
            closeables.asReversed().forEach { runCatching { it.close() } }
        }
    }

    private fun writeMonoWav(signal: FloatSignal, sampleRate: Int, file: File) {
        var peak = 0f
        for (i in 0 until signal.size) peak = maxOf(peak, kotlin.math.abs(signal[i]))
        val scale = if (peak > 1f) 1f / peak else 1f // como clip='rescale' de Demucs
        Pcm16MonoWavWriter(file, sampleRate, signal.size).use { w ->
            val block = ShortArray(8192)
            var i = 0
            while (i < signal.size) {
                val n = minOf(block.size, signal.size - i)
                for (k in 0 until n) block[k] = (signal[i + k] * scale * 32767).roundToInt().toShort()
                w.write(block, n)
                i += n
            }
        }
    }

    companion object {
        const val SR = DemucsSpectrogram.SAMPLE_RATE

        // Peso aproximado de cada etapa en el tiempo total (medido en PC: domina la separación).
        private val WEIGHTS = mapOf(
            Stage.DECODING to 0.02, Stage.RESAMPLING to 0.03, Stage.SEPARATION to 0.80,
            Stage.TRANSCRIPTION_VOICE to 0.05, Stage.TRANSCRIPTION_GUITAR to 0.05, Stage.PERCUSSION to 0.02,
            Stage.SYNTHESIS to 0.01, Stage.VALIDATION to 0.01, Stage.EXPORT to 0.01,
        )

        private fun overall(stage: Stage, fraction: Double): Double {
            val before = Stage.entries.takeWhile { it != stage }.sumOf { WEIGHTS.getValue(it) }
            return (before + WEIGHTS.getValue(stage) * fraction).coerceIn(0.0, 1.0)
        }
    }
}
