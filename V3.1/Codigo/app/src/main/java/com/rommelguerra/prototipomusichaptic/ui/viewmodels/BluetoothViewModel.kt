package com.rommelguerra.prototipomusichaptic.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rommelguerra.prototipomusichaptic.models.AppGlobals

class BluetoothViewModel : ViewModel() {
    var isConnected by mutableStateOf(AppGlobals.estaBrazaleteConectado)
        private set

    var deviceName by mutableStateOf("Desconectado")
        private set

    fun updateConnectionStatus(connected: Boolean, name: String = "ESP32 Vibrafil") {
        isConnected = connected
        deviceName = if (connected) name else "Desconectado"
        AppGlobals.estaBrazaleteConectado = connected
    }
}
