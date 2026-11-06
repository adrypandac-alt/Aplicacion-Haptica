/**
 * UNIVERSIDAD TECNOLÓGICA INDOAMÉRICA
 * PRÁCTICAS DE SERVICIO COMUNITARIO — PROYECTO MUSICHAPTIC
 */

package com.rommelguerra.prototipomusichaptic.algoritmo

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchDetectionResult
import be.tarsos.dsp.pitch.PitchProcessor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

/**
 * Modelo de datos que representa una nota musical decodificada y su traducción háptica exacta.
 */
data class NotaHaptica(
    val nota: String,
    val frecuenciaHz: Double,
    val ubicacionEspacial: Double,
    val amplitudSugerida: Int,
    val duracionMs: Long,
    val tiempoInicioS: Double,
) {
    fun toBleJsonString(): String {
        val json = JSONObject()
        json.put("n", nota)
        json.put("f", frecuenciaHz)
        json.put("u", ubicacionEspacial)
        json.put("a", amplitudSugerida)
        json.put("d", duracionMs)
        return json.toString()
    }
}

class AnalizadorAudioKotlin {

    companion object {
        private val MAPEO_TRIPLE_CODIFICACION = mapOf(
            "DO"   to Triple(62.15,  0.0,  255),
            "DO#"  to Triple(75.82,  0.9,  245),
            "RE"   to Triple(92.50,  1.8,  240),
            "RE#"  to Triple(112.85, 2.7,  235),
            "MI"   to Triple(137.68, 3.6,  230),
            "FA"   to Triple(167.97, 4.5,  225),
            "FA#"  to Triple(204.92, 5.4,  220),
            "SOL"  to Triple(250.00, 6.3,  225),
            "SOL#" to Triple(305.00, 7.2,  230),
            "LA"   to Triple(372.10, 8.1,  235),
            "LA#"  to Triple(453.96, 9.0,  245),
            "SI"   to Triple(553.83, 10.0, 255),
        )

        fun convertirFrecuenciaANota(frecuenciaObservadaHz: Double, tiempoS: Double, duracionMs: Long): NotaHaptica? {
            // Rango útil real de la tabla: DO (62.15) a SI (553.83), con margen
            if (frecuenciaObservadaHz < 45.0 || frecuenciaObservadaHz > 650.0) {
                return null
            }

            var notaSeleccionada = "DO"
            var menorDiferenciaRelativa = Double.MAX_VALUE
            var frecuenciaReferencia = 62.15
            var ubicacion = 0.0
            var amplitud = 255

            // Sin plegado de octava: se compara directo contra los 12 puntos reales de la tabla
            for ((nombreNota, atributos) in MAPEO_TRIPLE_CODIFICACION) {
                val (frecRef, ubiRef, ampRef) = atributos
                // Diferencia RELATIVA (%), no absoluta en Hz — evita que las notas graves
                // "atraigan" de más solo porque están más cerca en Hz nominales
                val diferenciaRelativa = abs(frecuenciaObservadaHz - frecRef) / frecRef
                if (diferenciaRelativa < menorDiferenciaRelativa) {
                    menorDiferenciaRelativa = diferenciaRelativa
                    notaSeleccionada = nombreNota
                    frecuenciaReferencia = frecRef
                    ubicacion = ubiRef
                    amplitud = ampRef
                }
            }

            // Tolerancia: si la nota más cercana está a más del 12% de diferencia, descartar
            // (evita forzar ruido/silencio/errores de octava de YIN a una nota falsa)
            if (menorDiferenciaRelativa > 0.12) {
                return null
            }

            return NotaHaptica(
                nota = notaSeleccionada,
                frecuenciaHz = frecuenciaReferencia,
                ubicacionEspacial = ubicacion,
                amplitudSugerida = amplitud,
                duracionMs = duracionMs,
                tiempoInicioS = tiempoS
            )
        }
    }

    private var dispatcherActivo: AudioDispatcher? = null

    fun procesarArchivoAudio(
        rutaArchivo: String,
        onNotaDetectada: (NotaHaptica) -> Unit,
        onProgreso: (Int) -> Unit,
        onCompletado: (List<NotaHaptica>) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val archivo = File(rutaArchivo)
                if (!archivo.exists()) {
                    withContext(Dispatchers.Main) { onError("Archivo no encontrado") }
                    return@launch
                }

                val tasaMuestreo = 22050
                val tamanoVentana = 4096
                val solapamiento = 2048

                // Obtener duración total para el progreso
                val duracionTotalS = obtenerDuracionAudioS(rutaArchivo)

                // DECODIFICACIÓN NATIVA ANDROID (MediaCodec)
                val pcmData = decodificarAudioNativo(rutaArchivo, tasaMuestreo)
                
                if (pcmData == null || pcmData.isEmpty()) {
                    withContext(Dispatchers.Main) { onError("No se pudo decodificar el audio.") }
                    return@launch
                }

                val format = TarsosDSPAudioFormat(tasaMuestreo.toFloat(), 16, 1, true, false)
                val stream = UniversalAudioInputStream(ByteArrayInputStream(pcmData), format)
                dispatcherActivo = AudioDispatcher(stream, tamanoVentana, solapamiento)
                
                val notasDetectadas = mutableListOf<NotaHaptica>()
                var ultimaNotaRegistrada: NotaHaptica? = null

                val handler = PitchDetectionHandler { result: PitchDetectionResult, event: AudioEvent ->
                    val pitchHz = result.pitch.toDouble()
                    val probabilidad = result.probability.toDouble()
                    val tiempoActualS = event.timeStamp
                    
                    // Calcular y reportar progreso
                    if (duracionTotalS > 0) {
                        val progreso = ((tiempoActualS / duracionTotalS) * 100).toInt().coerceIn(0, 100)
                        CoroutineScope(Dispatchers.Main).launch { onProgreso(progreso) }
                    }

                    if (pitchHz != -1.0 && probabilidad > 0.85 && event.rms > 0.05) {
                        convertirFrecuenciaANota(pitchHz, tiempoActualS, 350L)?.let { notaHaptica ->
                            if (ultimaNotaRegistrada == null || ultimaNotaRegistrada?.nota != notaHaptica.nota || (tiempoActualS - (ultimaNotaRegistrada?.tiempoInicioS ?: 0.0)) > 0.3) {
                                ultimaNotaRegistrada = notaHaptica
                                notasDetectadas.add(notaHaptica)
                                CoroutineScope(Dispatchers.Main).launch { onNotaDetectada(notaHaptica) }
                            }
                        }
                    }
                }

                val procesadorPitch = PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.YIN, tasaMuestreo.toFloat(), tamanoVentana, handler)
                dispatcherActivo?.addAudioProcessor(procesadorPitch)
                dispatcherActivo?.run()

                withContext(Dispatchers.Main) { onCompletado(notasDetectadas) }
            } catch (ex: Throwable) {
                withContext(Dispatchers.Main) { 
                    Log.e("AUDIO_ERROR", "Error fatal en análisis", ex)
                    onError("Error de sistema: ${ex.localizedMessage ?: "Falta binario FFmpeg"}") 
                }
            }
        }
    }

    fun detenerAnalisis() {
        dispatcherActivo?.stop()
        dispatcherActivo = null
    }

    /**
     * Obtiene la duración de un archivo de audio en segundos.
     */
    private fun obtenerDuracionAudioS(ruta: String): Double {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(ruta)
            val format = extractor.getTrackFormat(0)
            val duracionUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            extractor.release()
            duracionUs / 1_000_000.0
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * Decodifica un archivo de audio (MP3, WAV, etc.) a PCM crudo usando MediaCodec de Android.
     */
    private fun decodificarAudioNativo(ruta: String, sampleRateEsperado: Int): ByteArray? {
        val extractor = MediaExtractor()
        val outputStream = ByteArrayOutputStream()
        try {
            extractor.setDataSource(ruta)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            
            // Forzar salida Mono y la tasa de muestreo que configuramos en Tarsos
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRateEsperado)
            
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false

            while (!isEOS) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputStream.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            }
            decoder.stop()
            decoder.release()
            extractor.release()
            return outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e("DECODER", "Error decodificando audio nativo: ${e.message}")
            return null
        }
    }

    fun exportarSecuenciaAJsonBaseDatos(nombreCancion: String, notas: List<NotaHaptica>): String {
        val root = JSONObject()
        root.put("cancion", nombreCancion)
        root.put("total_notas", notas.size)
        val arrayNotas = JSONArray()
        for (nota in notas) {
            val item = JSONObject()
            item.put("n", nota.nota); item.put("f", nota.frecuenciaHz); item.put("u", nota.ubicacionEspacial)
            item.put("a", nota.amplitudSugerida); item.put("d", nota.duracionMs); item.put("t", nota.tiempoInicioS)
            arrayNotas.put(item)
        }
        root.put("secuencia", arrayNotas)
        return root.toString()
    }
}
