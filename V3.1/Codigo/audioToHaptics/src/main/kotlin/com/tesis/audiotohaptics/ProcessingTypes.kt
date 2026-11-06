package com.tesis.audiotohaptics

import com.tesis.audiotohaptics.config.Actuator
import com.tesis.audiotohaptics.validation.ValidationReport
import kotlinx.serialization.Serializable
import java.io.File

/** Etapas del pipeline, en orden. */
enum class Stage { DECODING, RESAMPLING, SEPARATION, TRANSCRIPTION_VOICE, TRANSCRIPTION_GUITAR, PERCUSSION, SYNTHESIS, VALIDATION, EXPORT }

/** Progreso: [fraction] en [0, 1] dentro de la etapa; [overall] aproximado del total. */
data class ProcessingProgress(val stage: Stage, val fraction: Double, val overall: Double, val message: String = "")

/** Estado de una fuente en el resultado. */
@Serializable
enum class SourceStatus { OK, NO_EVENTS }

data class ActuatorOutput(
    val actuator: Actuator,
    val actuatorId: Int,
    val carrierHz: Double,
    val status: SourceStatus,
    val wavFile: File,
    /** MIDI de la transcripción (voz y guitarra); null para percusión. */
    val midiFile: File?,
    /** CSV de eventos (notas o golpes). */
    val eventsFile: File,
    val eventCount: Int,
    val validation: ValidationReport,
)

data class ProcessingResult(
    val outputDirectory: File,
    val manifestFile: File,
    val outputs: Map<Actuator, ActuatorOutput>,
    val durationS: Double,
    val totalSamples: Int,
    val configConfirmed: Boolean,
    val warnings: List<String>,
    /** Tiempo de cada etapa en segundos. */
    val stageSeconds: Map<Stage, Double>,
    val stemFiles: Map<String, File>,
)
