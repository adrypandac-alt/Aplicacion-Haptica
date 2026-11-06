package com.tesis.audiotohaptics

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import com.tesis.audiotohaptics.audio.AndroidAudioDecoder
import com.tesis.audiotohaptics.config.HapticsConfig
import com.tesis.audiotohaptics.config.HapticsException
import com.tesis.audiotohaptics.pipeline.HapticsPipeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * API pública: transforma una canción local en tres WAV vibrotáctiles (voz, guitarra,
 * percusión) completamente en el dispositivo, sin conexión a internet.
 *
 * ```
 * val haptics = AudioToHaptics(context, HapticsConfig.fromJson(json))
 * val result = haptics.process(uri) { p -> progressBar.progress = (p.overall * 100).toInt() }
 * result.outputs[Actuator.VOICE]?.wavFile
 * ```
 *
 * - [process] es una función suspendida que trabaja en [dispatcher] (por defecto
 *   Dispatchers.Default), nunca en el hilo principal. Cancelar la corrutina detiene el
 *   procesamiento entre segmentos.
 * - Los errores se lanzan como subclases de [HapticsException].
 * - No se implementa comunicación BLE/ESP32: el protocolo no está disponible.
 */
class AudioToHaptics(
    context: Context,
    private val config: HapticsConfig,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val appContext = context.applicationContext
    private val models = ModelStore(appContext)

    /** Indica si los modelos están incluidos en la app (no comprueba la red: no la usa). */
    fun modelStatus(): ModelStore.Status = models.status()

    /**
     * @param input URI local (content:// o file://) de un M4A, MP3, WAV u otro formato soportado.
     * @param outputDir carpeta de resultados; por defecto filesDir/audiotohaptics/<marca de tiempo>.
     */
    suspend fun process(
        input: Uri,
        outputDir: File? = null,
        onProgress: (ProcessingProgress) -> Unit = {},
    ): ProcessingResult = withContext(dispatcher) {
        config.validate()
        val out = outputDir ?: File(appContext.filesDir, "audiotohaptics/${System.currentTimeMillis()}")
        val work = File(appContext.cacheDir, "audiotohaptics_work_${System.nanoTime()}")
        val ctx = coroutineContext
        val cancelCheck = { ctx.ensureActive() }
        try {
            val modelFiles = models.prepare()
            checkMemory()
            onProgress(ProcessingProgress(Stage.DECODING, 0.0, 0.0))
            val audio = AndroidAudioDecoder(appContext).decode(input, work, { f ->
                onProgress(ProcessingProgress(Stage.DECODING, f, 0.02 * f))
            }, cancelCheck)
            try {
                HapticsPipeline(config, modelFiles, work).run(audio, out, onProgress, cancelCheck)
            } finally {
                audio.channels.forEach { (it as? AutoCloseable)?.close() }
            }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun checkMemory() {
        if (config.minAvailableMemoryBytes <= 0) return
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        if (info.availMem < config.minAvailableMemoryBytes) {
            throw HapticsException.InsufficientMemory(info.availMem, config.minAvailableMemoryBytes)
        }
    }
}
