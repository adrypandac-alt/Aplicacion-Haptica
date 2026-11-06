package com.tesis.audiotohaptics.pipeline

import com.tesis.audiotohaptics.ActuatorOutput
import com.tesis.audiotohaptics.Stage
import com.tesis.audiotohaptics.config.Actuator
import com.tesis.audiotohaptics.config.EnvelopeConfig
import com.tesis.audiotohaptics.config.HapticsConfig
import com.tesis.audiotohaptics.config.PercussionConfig
import com.tesis.audiotohaptics.percussion.OnsetDetection
import com.tesis.audiotohaptics.synthesis.HapticEvent
import com.tesis.audiotohaptics.synthesis.SynthesisReport
import com.tesis.audiotohaptics.validation.SignalValidator
import com.tesis.audiotohaptics.validation.ValidationReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * manifest.json: metadatos de los tres WAV. Mantiene los campos del contrato
 * Practica/docs/CONTRATO_INTEGRACION.md (v0.1.0) y añade la información del
 * procesamiento en el dispositivo (contrato 0.2.0).
 */
internal object ManifestWriter {
    const val CONTRACT_VERSION = "0.2.0"

    @Serializable
    data class TimeReference(val origin: String, val durationS: Double, val nSamples: Int, val inputSampleRateHz: Int)

    @Serializable
    data class WavFormat(val encoding: String, val channels: Int, val sampleRateHz: Int)

    @Serializable
    data class Detection(val rawOnsets: Int, val removedLowLevel: Int, val removedDuplicates: Int, val finalOnsets: Int)

    @Serializable
    data class SourceEntry(
        val actuatorId: Int,
        val carrierHz: Double,
        val status: String,
        val file: String,
        val midiFile: String?,
        val eventsFile: String,
        val eventCount: Int,
        val activeTimeS: Double,
        val peakPcm: Int,
        val validation: ValidationReport,
        val detection: Detection? = null,
    )

    @Serializable
    data class Manifest(
        val contractVersion: String,
        val source: String,
        val processedOnDevice: Boolean,
        val networkUsed: Boolean,
        val configStatus: String,
        val configConfirmed: Boolean,
        val timeReference: TimeReference,
        val wavFormat: WavFormat,
        val maxAmplitude: Double,
        val envelope: EnvelopeConfig,
        val percussionParams: PercussionConfig,
        val models: Map<String, String>,
        val sources: Map<String, SourceEntry>,
        val warnings: List<String>,
        val stageSeconds: Map<String, Double>,
    )

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun write(
        file: File,
        config: HapticsConfig,
        audio: DecodedAudio,
        durationS: Double,
        totalSamples: Int,
        outputs: Map<Actuator, ActuatorOutput>,
        events: Map<Actuator, List<HapticEvent>>,
        reports: Map<Actuator, SynthesisReport>,
        detection: OnsetDetection,
        warnings: List<String>,
        timings: Map<Stage, Double>,
    ) {
        val sources = outputs.entries.associate { (a, o) ->
            val activeS = SignalValidator.mergedIntervals(events.getValue(a), 1.0 / o.carrierHz)
                .sumOf { minOf(it.second, durationS) - minOf(it.first, durationS) }
            a.name.lowercase() to SourceEntry(
                actuatorId = o.actuatorId,
                carrierHz = o.carrierHz,
                status = o.status.name.lowercase(),
                file = o.wavFile.name,
                midiFile = o.midiFile?.name,
                eventsFile = o.eventsFile.name,
                eventCount = o.eventCount,
                activeTimeS = round6(activeS),
                peakPcm = reports.getValue(a).peakPcm,
                validation = o.validation,
                detection = if (a == Actuator.PERCUSSION) Detection(
                    detection.rawCount, detection.removedLowLevel, detection.removedDuplicates, detection.onsets.size,
                ) else null,
            )
        }
        val manifest = Manifest(
            contractVersion = CONTRACT_VERSION,
            source = audio.sourceName,
            processedOnDevice = true,
            networkUsed = false,
            configStatus = config.status.name,
            configConfirmed = config.confirmed,
            timeReference = TimeReference(
                "t=0 en la primera muestra de la canción original", round6(durationS), totalSamples, audio.sampleRate,
            ),
            wavFormat = WavFormat("PCM signed 16-bit little-endian", 1, config.sampleRateHz!!),
            maxAmplitude = config.maxAmplitude,
            envelope = config.envelope,
            percussionParams = config.percussion,
            models = mapOf(
                "separation" to "htdemucs_6s (Demucs v4, MIT) exportado a ONNX",
                "transcription" to "Basic Pitch icassp_2022 nmp.onnx (Spotify, Apache-2.0)",
                "inference" to "ONNX Runtime (CPU, local)",
            ),
            sources = sources,
            warnings = warnings,
            stageSeconds = timings.entries.associate { it.key.name.lowercase() to round6(it.value) },
        )
        file.writeText(json.encodeToString(Manifest.serializer(), manifest))
    }

    private fun round6(v: Double) = Math.round(v * 1e6) / 1e6
}
