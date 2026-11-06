package com.tesis.audiotohaptics.config

/** Errores de la biblioteca. Todos son recuperables por la app que la integra. */
sealed class HapticsException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Faltan parámetros de hardware (portadoras, frecuencia de muestreo). */
    class ConfigurationPending(val missing: List<String>) :
        HapticsException("Parámetros pendientes de confirmación con el equipo de hardware: ${missing.joinToString()}")

    class InvalidConfiguration(val problems: List<String>) :
        HapticsException("Configuración inválida: ${problems.joinToString("; ")}")

    /** El archivo no se puede leer o su códec no está disponible en el dispositivo. */
    class UnsupportedAudio(message: String, cause: Throwable? = null) : HapticsException(message, cause)

    /** El modelo no está instalado (p. ej. la app no incluye el paquete audioToHapticsModels). */
    class ModelNotAvailable(val model: String, message: String) : HapticsException(message)

    class InsufficientMemory(val availableBytes: Long, val requiredBytes: Long) : HapticsException(
        "Memoria libre insuficiente para la separación: ${availableBytes / MB} MB disponibles, " +
            "se requieren ~${requiredBytes / MB} MB"
    )

    class ProcessingFailed(stage: String, cause: Throwable) :
        HapticsException("Falló la etapa '$stage': ${cause.message}", cause)

    private companion object { const val MB = 1024L * 1024 }
}
