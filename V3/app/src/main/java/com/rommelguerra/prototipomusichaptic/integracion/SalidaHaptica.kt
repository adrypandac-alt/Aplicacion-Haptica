package com.rommelguerra.prototipomusichaptic.integracion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** PUNTO DE INTEGRACIÓN ACTUADORES: sustituya esta salida en IntegracionViewModel
 * por un adaptador BLE cuando se haya definido y probado el protocolo del hardware.
 * No envíe valores de frecuencia/ubicación inventados a los dos actuadores.
 */
interface SalidaHaptica {
    fun reproducir(patron: PatronHaptico, intensidad: Float)
    fun detener()
}

class SalidaHapticaTelefono(context: Context) : SalidaHaptica {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    override fun reproducir(patron: PatronHaptico, intensidad: Float) {
        if (!vibrator.hasVibrator()) return
        val times = patron.tiemposMs.toLongArray()
        if (Build.VERSION.SDK_INT >= 26) {
            val strength = (patron.amplitud * intensidad.coerceIn(0.1f, 1f)).toInt().coerceIn(1, 255)
            val amplitudes = IntArray(times.size) { if (it % 2 == 0) 0 else strength }
            vibrator.vibrate(VibrationEffect.createWaveform(times, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(times, -1)
        }
    }

    override fun detener() = vibrator.cancel()
}
