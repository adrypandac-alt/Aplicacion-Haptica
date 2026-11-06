package com.tesis.audiotohaptics.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Fuente musical y actuador asociado. */
@Serializable
enum class Actuator(val defaultId: Int, val defaultFile: String) {
    VOICE(1, "voice_actuator.wav"),
    GUITAR(2, "guitar_actuator.wav"),
    PERCUSSION(3, "percussion_actuator.wav"),
}

@Serializable
enum class OverlapPolicy { MAX, SUM_NORMALIZED }

@Serializable
enum class ConfigStatus { PENDING, TEST_VALUES_NOT_CONFIRMED, CONFIRMED }

@Serializable
data class ActuatorConfig(
    val actuatorId: Int,
    /** Frecuencia portadora FIJA del actuador (Hz). null = pendiente de la ficha técnica. */
    val carrierHz: Double? = null,
    val outputFile: String,
)

@Serializable
data class EnvelopeConfig(
    val attackS: Double = 0.005,
    val releaseS: Double = 0.010,
    val overlapPolicy: OverlapPolicy = OverlapPolicy.MAX,
    val mapVelocity: Boolean = false,
    val velocityMinGain: Double = 0.3,
    val minEventDurationS: Double = 0.0,
)

@Serializable
data class PercussionConfig(
    val pulseDurationS: Double = 0.05,
    val minIntervalS: Double = 0.08,
    val onsetDelta: Double = 0.07,
    val minLevelDb: Double = -40.0,
    val minAbsoluteDbfs: Double = -50.0,
    val hopLength: Int = 256,
)

/**
 * Configuración completa del procesamiento.
 *
 * Los parámetros de hardware (portadoras y frecuencia de muestreo) no figuran en ninguna
 * documentación disponible del proyecto: en [default] son null y [validate] los rechaza.
 * [testValues] contiene valores de PRUEBA explícitamente no confirmados.
 */
@Serializable
data class HapticsConfig(
    val status: ConfigStatus = ConfigStatus.PENDING,
    /** Frecuencia de muestreo de los WAV de salida (Hz). No confundir con la portadora. */
    val sampleRateHz: Int? = null,
    val maxAmplitude: Double = 0.9,
    val envelope: EnvelopeConfig = EnvelopeConfig(),
    val percussion: PercussionConfig = PercussionConfig(),
    val actuators: Map<Actuator, ActuatorConfig> = Actuator.entries.associateWith {
        ActuatorConfig(it.defaultId, null, it.defaultFile)
    },
    /** Hilos de ONNX Runtime (0 = decide ORT). */
    val inferenceThreads: Int = 0,
    /** Memoria libre mínima (bytes) antes de la separación; 0 desactiva la comprobación. */
    val minAvailableMemoryBytes: Long = DEFAULT_MIN_MEMORY,
    /** Guarda las fuentes separadas (mono, PCM 16) para inspección. */
    val exportStems: Boolean = false,
) {
    val confirmed: Boolean get() = status == ConfigStatus.CONFIRMED

    /** Lanza [HapticsException.ConfigurationPending] o [HapticsException.InvalidConfiguration]. */
    fun validate() {
        val missing = buildList {
            if (sampleRateHz == null) add("sampleRateHz")
            for (a in Actuator.entries) {
                if (actuators[a]?.carrierHz == null) add("actuators.${a.name}.carrierHz")
            }
        }
        if (missing.isNotEmpty()) throw HapticsException.ConfigurationPending(missing)
        val problems = buildList {
            val sr = sampleRateHz!!
            if (sr <= 0) add("sampleRateHz debe ser > 0")
            if (maxAmplitude <= 0 || maxAmplitude > 1) add("maxAmplitude debe estar en (0, 1]")
            for ((a, c) in actuators) {
                val f = c.carrierHz!!
                if (f <= 0 || f >= sr / 2.0) add("${a.name}.carrierHz=$f debe estar en (0, ${sr / 2.0}) Hz")
            }
            if (envelope.attackS < 0 || envelope.releaseS < 0) add("las rampas no pueden ser negativas")
            if (envelope.velocityMinGain !in 0.0..1.0) add("velocityMinGain debe estar en [0, 1]")
            if (percussion.pulseDurationS <= 0 || percussion.minIntervalS < 0 || percussion.hopLength <= 0) {
                add("parámetros de percusión fuera de rango")
            }
        }
        if (problems.isNotEmpty()) throw HapticsException.InvalidConfiguration(problems)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        /** Medido en PC: pico de ~2,5 GB del proceso al separar un segmento (docs/INFORME_VIABILIDAD.md). */
        const val DEFAULT_MIN_MEMORY = 2_600L * 1024 * 1024

        private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

        /** Configuración oficial: parámetros de hardware pendientes (null). */
        fun default() = HapticsConfig()

        /**
         * VALORES DE PRUEBA, no confirmados por el fabricante ni por el firmware.
         * Portadoras distintas (150/200/250 Hz) para distinguir los canales en un espectro.
         */
        fun testValues() = HapticsConfig(
            status = ConfigStatus.TEST_VALUES_NOT_CONFIRMED,
            sampleRateHz = 44100,
            actuators = mapOf(
                Actuator.VOICE to ActuatorConfig(1, 150.0, Actuator.VOICE.defaultFile),
                Actuator.GUITAR to ActuatorConfig(2, 200.0, Actuator.GUITAR.defaultFile),
                Actuator.PERCUSSION to ActuatorConfig(3, 250.0, Actuator.PERCUSSION.defaultFile),
            ),
        )

        fun fromJson(text: String): HapticsConfig = json.decodeFromString(serializer(), text)
    }
}
