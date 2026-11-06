package com.rommelguerra.prototipomusichaptic.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.rommelguerra.prototipomusichaptic.data.BaseDatosHelper
import com.rommelguerra.prototipomusichaptic.models.AppGlobals
import com.rommelguerra.prototipomusichaptic.models.NoteModel
import org.json.JSONObject
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class HapticService(
    private val context: Context
) {

    // =========================================================
    // VIBRADOR DEL CELULAR
    // =========================================================

    private val celularVibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val vibratorManager =
                context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
                ) as? VibratorManager

            vibratorManager?.defaultVibrator

        } else {

            @Suppress("DEPRECATION")
            context.getSystemService(
                Context.VIBRATOR_SERVICE
            ) as? Vibrator
        }

    // =========================================================
    // HANDLER
    // =========================================================

    private val handler =
        Handler(Looper.getMainLooper())

    // =========================================================
    // EMITIR UNA NOTA / TACTÓN
    // =========================================================

    @SuppressLint("MissingPermission")
    @RequiresPermission(
        allOf = [
            Manifest.permission.VIBRATE,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    fun emitirTacton(
        nota: NoteModel
    ) {

        // -----------------------------------------------------
        // INTENSIDAD SELECCIONADA POR EL USUARIO
        // -----------------------------------------------------

        val factorIntensidad =
            when (
                AppGlobals.intensidadSeleccionada
            ) {

                "BAJA" ->
                    0.25f

                "MEDIA" ->
                    0.60f

                "ALTA" ->
                    1.0f

                else ->
                    0.60f
            }

        // -----------------------------------------------------
        // ESPACIALIDAD DESDE LA BD
        // -----------------------------------------------------

        val dbHelper =
            BaseDatosHelper(context)

        val configRastro =
            dbHelper.obtenerConfiguracionRastro(
                nota.nombre
            )

        val motorInicio =
            configRastro?.get(
                "motor_inicio"
            ) ?: "AMBOS"

        val motorDestino =
            configRastro?.get(
                "motor_destino"
            ) ?: "AMBOS"

        // -----------------------------------------------------
        // NUEVO:
        // ENVIAR FRECUENCIA + UBICACIÓN AL ESP32
        // -----------------------------------------------------

        enviarNotaAlBrazalete(
            nota = nota,
            factorIntensidad = factorIntensidad
        )

        // -----------------------------------------------------
        // VIBRACIÓN DEL CELULAR
        // -----------------------------------------------------

        if (
            AppGlobals.dispositivoSalida !=
            "BRAZALETE"
        ) {

            if (
                celularVibrator != null &&
                celularVibrator.hasVibrator()
            ) {

                ejecutarEfecto(
                    celularVibrator,
                    nota.patronVibracion,
                    nota.amplitudes,
                    factorIntensidad
                )
            }
        }

        // -----------------------------------------------------
        // AUDIO JACK
        // -----------------------------------------------------

        enviarSenalAudioJackDinamica(
            nota,
            factorIntensidad,
            motorInicio,
            motorDestino
        )
    }

    // =========================================================
    // NUEVO:
    // ENVIAR NOTA COMPLETA AL ESP32
    // =========================================================

    @SuppressLint("MissingPermission")
    @RequiresPermission(
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private fun enviarNotaAlBrazalete(
        nota: NoteModel,
        factorIntensidad: Float
    ) {

        val gatt =
            AppGlobals.gattBluetooth

        val characteristic =
            AppGlobals.characteristicComandos

        if (
            gatt == null ||
            characteristic == null
        ) {

            Log.w(
                "HapticService",
                "Brazalete BLE no conectado"
            )

            return
        }

        try {

            // ==============================================
            // INTENSIDAD
            // ==============================================

            val intensidadPorcentaje =
                (factorIntensidad * 100f)
                    .toInt()
                    .coerceIn(
                        1,
                        100
                    )

            // ==============================================
            // FRECUENCIA COMPACTA
            //
            // Ejemplo:
            // 62.147964530 -> 62.148
            // ==============================================

            val frecuencia =
                String.format(
                    Locale.US,
                    "%.3f",
                    nota.frecuenciaHz
                )

            // ==============================================
            // UBICACIÓN COMPACTA
            //
            // Multiplicamos x10:
            //
            // 0.0  -> 0
            // 0.9  -> 9
            // 1.8  -> 18
            // 4.5  -> 45
            // 10.0 -> 100
            // ==============================================

            val ubicacionCompacta =
                (nota.ubicacion * 10f)
                    .toInt()
                    .coerceIn(
                        0,
                        100
                    )

            // ==============================================
            // MENSAJE BLE
            //
            // NOTA|FRECUENCIA|INTENSIDAD|UBICACION
            //
            // Ejemplos:
            //
            // DO|62.148|60|0
            // FA|167.966|60|45
            // SI|553.834|60|100
            // ==============================================

            val commandStr =
                "${nota.nombre.uppercase()}|" +
                        "$frecuencia|" +
                        "$intensidadPorcentaje|" +
                        "$ubicacionCompacta"

            val command =
                commandStr.toByteArray(
                    Charsets.UTF_8
                )

            // ==============================================
            // DEBUG
            // ==============================================

            Log.d(
                "HapticService",
                "BLE -> $commandStr"
            )

            Log.d(
                "HapticService",
                "Longitud BLE -> ${command.size} bytes"
            )

            // ==============================================
            // ENVÍO BLE
            // ==============================================

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                gatt.writeCharacteristic(
                    characteristic,
                    command,
                    BluetoothGattCharacteristic
                        .WRITE_TYPE_DEFAULT
                )

            } else {

                @Suppress("DEPRECATION")
                characteristic.value =
                    command

                @Suppress("DEPRECATION")
                characteristic.writeType =
                    BluetoothGattCharacteristic
                        .WRITE_TYPE_DEFAULT

                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(
                    characteristic
                )
            }

        } catch (
            e: SecurityException
        ) {

            Log.e(
                "HapticService",
                "Error de permisos BLE",
                e
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                "HapticService",
                "Error enviando nota por BLE",
                e
            )
        }
    }

    // =========================================================
    // ENVÍO BLE DIRECTO
    // =========================================================

    @SuppressLint("MissingPermission")
    @RequiresPermission(
        Manifest.permission.BLUETOOTH_CONNECT
    )
    fun enviarComandoBle(
        jsonPayload: String
    ) {

        val gatt =
            AppGlobals.gattBluetooth

        val characteristic =
            AppGlobals.characteristicComandos

        if (
            gatt != null &&
            characteristic != null
        ) {

            try {

                val data =
                    jsonPayload.toByteArray(
                        Charsets.UTF_8
                    )

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {

                    gatt.writeCharacteristic(
                        characteristic,
                        data,
                        BluetoothGattCharacteristic
                            .WRITE_TYPE_DEFAULT
                    )

                } else {

                    @Suppress("DEPRECATION")
                    characteristic.value =
                        data

                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(
                        characteristic
                    )
                }

            } catch (
                e: Exception
            ) {

                Log.e(
                    "HapticService",
                    "Error enviando BLE Directo",
                    e
                )
            }
        }
    }

    // =========================================================
    // REPRODUCIR SECUENCIA
    // =========================================================

    @RequiresPermission(
        allOf = [
            Manifest.permission.VIBRATE,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    fun reproducirSecuencia(
        tramaJson: String
    ) {

        detenerSecuencia()

        try {

            val root =
                JSONObject(tramaJson)

            val secuencia =
                root.getJSONArray(
                    "secuencia"
                )

            var delayAcumulado =
                0L

            for (
            i in 0 until secuencia.length()
            ) {

                val notaJson =
                    secuencia.getJSONObject(i)

                val duracion =
                    notaJson.getInt("d")

                val notaModel =
                    NoteModel(

                        nombre =
                            notaJson.getString("n"),

                        descripcionVisual =
                            "",

                        patronVibracion =
                            longArrayOf(
                                0L,
                                duracion.toLong()
                            ),

                        amplitudes =
                            intArrayOf(
                                0,
                                notaJson.optInt(
                                    "a",
                                    255
                                )
                            ),

                        frecuenciaHz =
                            notaJson.getDouble("f"),

                        ubicacion =
                            notaJson
                                .getDouble("u")
                                .toFloat()
                    )

                handler.postDelayed(
                    {
                        emitirTacton(
                            notaModel
                        )
                    },
                    delayAcumulado
                )

                delayAcumulado +=
                    duracion + 50L
            }

        } catch (
            e: Exception
        ) {

            Log.e(
                "HapticService",
                "Error secuencia",
                e
            )
        }
    }

    // =========================================================
    // DETENER SECUENCIA
    // =========================================================

    fun detenerSecuencia() {

        handler.removeCallbacksAndMessages(
            null
        )

        celularVibrator?.cancel()
    }

    // =========================================================
    // AUDIO JACK CON ESPACIALIDAD
    // =========================================================

    private fun enviarSenalAudioJackDinamica(
        nota: NoteModel,
        factor: Float,
        inicio: String,
        fin: String
    ) {

        val sampleRate =
            44100

        val frecuenciaHz =
            nota.frecuenciaHz

        val patron =
            nota.patronVibracion

        val amplitudes =
            nota.amplitudes

        val duracionTotalMs =
            patron.sum()

        if (
            duracionTotalMs <= 0
        ) {
            return
        }

        val numSamples =
            (
                    sampleRate *
                            (
                                    duracionTotalMs /
                                            1000.0
                                    )
                    ).toInt()

        val generatedSmp =
            ShortArray(
                numSamples * 2
            )

        // -----------------------------------------------------
        // POSICIÓN INICIAL
        // -----------------------------------------------------

        val volIzqStart =
            when (inicio) {

                "IZQ" ->
                    1.0f

                "DER" ->
                    0.05f

                else ->
                    0.70f
            }

        val volDerStart =
            when (inicio) {

                "DER" ->
                    1.0f

                "IZQ" ->
                    0.05f

                else ->
                    0.70f
            }

        // -----------------------------------------------------
        // POSICIÓN FINAL
        // -----------------------------------------------------

        val volIzqEnd =
            when (fin) {

                "IZQ" ->
                    1.0f

                "DER" ->
                    0.05f

                else ->
                    0.70f
            }

        val volDerEnd =
            when (fin) {

                "DER" ->
                    1.0f

                "IZQ" ->
                    0.05f

                else ->
                    0.70f
            }

        var currentSample =
            0

        // -----------------------------------------------------
        // GENERAR AUDIO
        // -----------------------------------------------------

        for (
        i in patron.indices
        ) {

            val duracionSegmentoMs =
                patron[i]

            val numSamplesSeg =
                (
                        sampleRate *
                                (
                                        duracionSegmentoMs /
                                                1000.0
                                        )
                        ).toInt()

            val ampBase =
                if (
                    i < amplitudes.size
                ) {

                    amplitudes[i]

                } else {

                    0
                }

            val volumenGlobal =
                (
                        ampBase /
                                255.0
                        ) * factor

            if (
                volumenGlobal > 0
            ) {

                val fadeSamples =
                    (
                            sampleRate *
                                    0.01
                            ).toInt()

                for (
                j in 0 until numSamplesSeg
                ) {

                    if (
                        currentSample >=
                        numSamples
                    ) {
                        break
                    }

                    val progresoTotal =
                        currentSample
                            .toFloat() /
                                numSamples
                                    .toFloat()

                    val volIzq =
                        volIzqStart +
                                (
                                        volIzqEnd -
                                                volIzqStart
                                        ) *
                                progresoTotal

                    val volDer =
                        volDerStart +
                                (
                                        volDerEnd -
                                                volDerStart
                                        ) *
                                progresoTotal

                    var envLocal =
                        1.0

                    if (
                        j < fadeSamples
                    ) {

                        envLocal =
                            j.toDouble() /
                                    fadeSamples
                    }

                    if (
                        j >
                        numSamplesSeg -
                        fadeSamples
                    ) {

                        envLocal =
                            (
                                    numSamplesSeg -
                                            j
                                    ).toDouble() /
                                    fadeSamples
                    }

                    val t =
                        currentSample
                            .toDouble() /
                                sampleRate

                    val sampleValue =
                        (
                                sin(
                                    2.0 *
                                            Math.PI *
                                            frecuenciaHz *
                                            t
                                ) *
                                        Short.MAX_VALUE *
                                        volumenGlobal *
                                        envLocal
                                ).toInt()
                            .coerceIn(
                                Short.MIN_VALUE.toInt(),
                                Short.MAX_VALUE.toInt()
                            )
                            .toShort()

                    generatedSmp[
                        currentSample * 2
                    ] =
                        (
                                sampleValue *
                                        volIzq
                                )
                            .toInt()
                            .coerceIn(
                                Short.MIN_VALUE.toInt(),
                                Short.MAX_VALUE.toInt()
                            )
                            .toShort()

                    generatedSmp[
                        currentSample * 2 + 1
                    ] =
                        (
                                sampleValue *
                                        volDer
                                )
                            .toInt()
                            .coerceIn(
                                Short.MIN_VALUE.toInt(),
                                Short.MAX_VALUE.toInt()
                            )
                            .toShort()

                    currentSample++
                }

            } else {

                for (
                j in 0 until numSamplesSeg
                ) {

                    if (
                        currentSample >=
                        numSamples
                    ) {
                        break
                    }

                    generatedSmp[
                        currentSample * 2
                    ] = 0

                    generatedSmp[
                        currentSample * 2 + 1
                    ] = 0

                    currentSample++
                }
            }
        }

        // -----------------------------------------------------
        // REPRODUCIR AUDIO
        // -----------------------------------------------------

        try {

            val audioTrack =
                AudioTrack.Builder()

                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(
                                AudioAttributes
                                    .USAGE_MEDIA
                            )
                            .setContentType(
                                AudioAttributes
                                    .CONTENT_TYPE_MUSIC
                            )
                            .build()
                    )

                    .setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(
                                AudioFormat
                                    .ENCODING_PCM_16BIT
                            )
                            .setSampleRate(
                                sampleRate
                            )
                            .setChannelMask(
                                AudioFormat
                                    .CHANNEL_OUT_STEREO
                            )
                            .build()
                    )

                    .setBufferSizeInBytes(
                        generatedSmp.size * 2
                    )

                    .setTransferMode(
                        AudioTrack.MODE_STATIC
                    )

                    .build()

            audioTrack.write(
                generatedSmp,
                0,
                generatedSmp.size
            )

            audioTrack.play()

            Handler(
                Looper.getMainLooper()
            ).postDelayed(
                {

                    try {

                        audioTrack.stop()
                        audioTrack.release()

                    } catch (
                        e: Exception
                    ) {
                        // Sin acción.
                    }
                },

                duracionTotalMs + 200
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                "HapticService",
                "AudioTrack Error",
                e
            )
        }
    }

    // =========================================================
    // VIBRACIÓN DEL CELULAR
    // =========================================================

    private fun ejecutarEfecto(
        vibrator: Vibrator,
        patron: LongArray,
        amplitudesBase: IntArray,
        factor: Float
    ) {

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                val duraciones =
                    patron.map {

                        if (
                            it < 0
                        ) {
                            0L
                        } else {
                            it
                        }

                    }.toLongArray()

                val amplitudesFinales =
                    IntArray(
                        duraciones.size
                    ) { i ->

                        val ampOriginal =
                            if (
                                i <
                                amplitudesBase.size
                            ) {

                                amplitudesBase[i]

                            } else {

                                0
                            }

                        (
                                ampOriginal *
                                        factor
                                )
                            .toInt()
                            .coerceIn(
                                0,
                                255
                            )
                    }

                vibrator.cancel()

                vibrator.vibrate(
                    VibrationEffect
                        .createWaveform(
                            duraciones,
                            amplitudesFinales,
                            -1
                        )
                )

            } else {

                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    patron,
                    -1
                )
            }

        } catch (
            e: Exception
        ) {

            Log.e(
                "HapticService",
                "Error ejecutando vibración",
                e
            )
        }
    }
}