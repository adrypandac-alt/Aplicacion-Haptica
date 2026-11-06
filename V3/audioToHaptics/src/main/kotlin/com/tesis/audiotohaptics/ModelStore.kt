package com.tesis.audiotohaptics

import android.content.Context
import com.tesis.audiotohaptics.config.HapticsException
import com.tesis.audiotohaptics.pipeline.ModelFiles
import java.io.File
import java.io.FileNotFoundException

/**
 * Localiza los modelos en los assets de la app (incluidos al instalar) y los copia una
 * sola vez al almacenamiento interno para que ONNX Runtime los abra por ruta.
 *
 * NUNCA descarga nada: si un modelo no está en los assets, lanza ModelNotAvailable.
 *  - basic_pitch_nmp.onnx  -> incluido en el AAR audioToHaptics.
 *  - htdemucs_6s.onnx      -> incluido en el AAR audioToHapticsModels (paquete de modelos).
 */
class ModelStore(private val context: Context) {

    data class Status(val demucsInstalled: Boolean, val basicPitchInstalled: Boolean) {
        val ready: Boolean get() = demucsInstalled && basicPitchInstalled
    }

    fun status() = Status(assetExists(DEMUCS), assetExists(BASIC_PITCH))

    fun prepare(): ModelFiles = ModelFiles(extract(DEMUCS), extract(BASIC_PITCH))

    private fun assetExists(name: String): Boolean = try {
        context.assets.openFd("$ASSET_DIR/$name").use { true }
    } catch (_: FileNotFoundException) {
        // openFd falla también si el asset está comprimido; se intenta abrir como stream.
        runCatching { context.assets.open("$ASSET_DIR/$name").use { true } }.getOrDefault(false)
    } catch (_: Exception) {
        false
    }

    private fun extract(name: String): File {
        if (!assetExists(name)) {
            throw HapticsException.ModelNotAvailable(
                name,
                "El modelo $name no está incluido en la app. Añada la dependencia " +
                    (if (name == DEMUCS) "audioToHapticsModels" else "audioToHaptics") + " (ver docs/INTEGRACION.md).",
            )
        }
        val dir = File(context.noBackupFilesDir, "audiotohaptics/models").apply { mkdirs() }
        val target = File(dir, name)
        val expected = assetLength(name)
        if (target.isFile && (expected < 0 || target.length() == expected)) return target
        val tmp = File(dir, "$name.tmp")
        context.assets.open("$ASSET_DIR/$name").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true); tmp.delete()
        }
        return target
    }

    private fun assetLength(name: String): Long =
        runCatching { context.assets.openFd("$ASSET_DIR/$name").use { it.length } }.getOrDefault(-1L)

    companion object {
        const val ASSET_DIR = "audiotohaptics/models"
        const val DEMUCS = "htdemucs_6s.onnx"
        const val BASIC_PITCH = "basic_pitch_nmp.onnx"
    }
}
