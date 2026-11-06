package com.rommelguerra.prototipomusichaptic.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rommelguerra.prototipomusichaptic.algoritmo.AnalizadorAudioKotlin
import com.rommelguerra.prototipomusichaptic.algoritmo.NotaHaptica
import com.rommelguerra.prototipomusichaptic.data.BaseDatosHelper
import com.rommelguerra.prototipomusichaptic.models.NoteRepository
import com.rommelguerra.prototipomusichaptic.services.HapticService
import java.io.File
import java.io.FileOutputStream

class FileModeViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = BaseDatosHelper(application)
    private val hapticService = HapticService(application)
    private val analizador = AnalizadorAudioKotlin()

    var fileName by mutableStateOf("Ninguno")
    var statusText by mutableStateOf("Esperando archivo...")
    var progress by mutableStateOf(0f)
    var isPlaying by mutableStateOf(false)
    var isAnalyzing by mutableStateOf(false)
    var canPlay by mutableStateOf(false)
    
    private var tramaJsonActual: String? = null

    fun loadFromLibrary(songName: String) {
        fileName = "Pieza: $songName"
        statusText = "Preparando secuencia de la biblioteca..."
        progress = 0.5f
        isAnalyzing = true

        val correo = getApplication<Application>().getSharedPreferences("SesionUsuario", android.content.Context.MODE_PRIVATE)
            .getString("correoUsuario", "") ?: ""
        val idUsuario = dbHelper.obtenerIdUsuario(correo)

        tramaJsonActual = dbHelper.obtenerTramaAnalizada(songName, idUsuario)

        if (tramaJsonActual == null) {
            val notasSecuencia = NoteRepository.obtenerSecuenciaCancion(songName, dbHelper)
            if (notasSecuencia.isNotEmpty()) {
                val notasHapticas = notasSecuencia.map { note ->
                    NotaHaptica(
                        nota = note.nombre,
                        frecuenciaHz = note.frecuenciaHz,
                        ubicacionEspacial = note.ubicacion.toDouble(),
                        amplitudSugerida = note.amplitudes.maxOrNull() ?: 255,
                        duracionMs = note.patronVibracion.filterIndexed { index, _ -> index % 2 != 0 }.sum(),
                        tiempoInicioS = 0.0
                    )
                }
                tramaJsonActual = analizador.exportarSecuenciaAJsonBaseDatos(songName, notasHapticas)
                tramaJsonActual?.let { dbHelper.registrarCancionConAnalisis(songName, idUsuario, it) }
            }
        }

        if (tramaJsonActual != null) {
            progress = 1f
            statusText = "¡Pieza lista para reproducir!"
            canPlay = true
        } else {
            statusText = "Error al cargar la pieza."
            progress = 0f
        }
        isAnalyzing = false
    }

    fun analyzeAudioFile(uri: Uri, contentResolver: android.content.ContentResolver, cacheDir: File) {
        val name = getFileName(uri, contentResolver) ?: "archivo_audio"
        fileName = name
        statusText = "Analizando audio..."
        isAnalyzing = true
        progress = 0.1f
        canPlay = false

        try {
            val tempFile = File(cacheDir, "analisis_temp")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            analizador.procesarArchivoAudio(
                rutaArchivo = tempFile.absolutePath,
                onNotaDetectada = { nota ->
                    try {
                        hapticService.enviarComandoBle(nota.toBleJsonString())
                    } catch (e: SecurityException) {
                        Log.e("FileModeViewModel", "Permisos de Bluetooth faltantes", e)
                    }
                },
                onProgreso = { p ->
                    progress = p / 100f
                    statusText = "Analizando: $p%"
                },
                onCompletado = { notas ->
                    tramaJsonActual = analizador.exportarSecuenciaAJsonBaseDatos(name, notas)
                    val correo = getApplication<Application>().getSharedPreferences("SesionUsuario", android.content.Context.MODE_PRIVATE)
                        .getString("correoUsuario", "") ?: ""
                    val idUser = dbHelper.obtenerIdUsuario(correo)
                    tramaJsonActual?.let { dbHelper.registrarCancionConAnalisis(name, idUser, it) }

                    progress = 1f
                    statusText = "¡Análisis completo! Guardado en biblioteca."
                    canPlay = true
                    isAnalyzing = false
                },
                onError = { error ->
                    statusText = "Error: $error"
                    isAnalyzing = false
                }
            )
        } catch (e: Exception) {
            statusText = "Error cargando archivo"
            isAnalyzing = false
        }
    }

    fun playSequence() {
        val trama = tramaJsonActual
        if (trama != null && !isPlaying) {
            isPlaying = true
            try {
                hapticService.reproducirSecuencia(trama)
                statusText = "Reproduciendo secuencia..."
            } catch (e: SecurityException) {
                isPlaying = false
            }
        }
    }

    fun stopSequence() {
        if (isPlaying) {
            hapticService.detenerSecuencia()
            isPlaying = false
            statusText = "Reproducción detenida"
        }
    }

    private fun getFileName(uri: Uri, contentResolver: android.content.ContentResolver): String? {
        var result: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        }
        return result ?: uri.lastPathSegment
    }

    override fun onCleared() {
        analizador.detenerAnalisis()
        dbHelper.close()
        super.onCleared()
    }
}
