package com.rommelguerra.prototipomusichaptic.models

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothSocket

object AppGlobals {
    // Configuración de usuario (Calibración)
    var intensidadSeleccionada: String = "MEDIA" // Valores: BAJA, MEDIA, ALTA
    var dispositivoSalida: String = "CELULAR"    // Valores: CELULAR, BRAZALETE

    // 🔵 Bluetooth Low Energy (BLE) - Compatibilidad con Prototipo ESP32
    var gattBluetooth: BluetoothGatt? = null
    var characteristicComandos: BluetoothGattCharacteristic? = null
    var estaBrazaleteConectado: Boolean = false

    // UUIDs según código Arduino (VIBRAFIL_ESP32)
    const val SERVICE_UUID: String = "12345678-1234-1234-1234-1234567890ab"
    const val COMMAND_UUID: String = "abcdefab-1234-5678-1234-abcdefabcdef"

    // Estadísticas de Progreso (Modo Aprender)
    var respuestasCorrectas: Int = 0
    var respuestasIncorrectas: Int = 0
    var rachaActual: Int = 0
    var rachaMaxima: Int = 0

    fun registrarAcierto() {
        respuestasCorrectas++
        rachaActual++
        if (rachaActual > rachaMaxima) rachaMaxima = rachaActual
    }

    fun registrarError() {
        respuestasIncorrectas++
        rachaActual = 0
    }
}
