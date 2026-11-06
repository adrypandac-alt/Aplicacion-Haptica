package com.rommelguerra.prototipomusichaptic.ui.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rommelguerra.prototipomusichaptic.data.BaseDatosHelper
import com.rommelguerra.prototipomusichaptic.models.AppGlobals
import com.rommelguerra.prototipomusichaptic.models.NoteModel
import com.rommelguerra.prototipomusichaptic.services.HapticService
import java.util.*

class MainMenuViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = BaseDatosHelper(application)
    private val hapticService = HapticService(application)
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val handler = Handler(Looper.getMainLooper())

    var userName by mutableStateOf("Usuario")
    var notes = mutableStateListOf<NoteModel>()
    var scannedDevices = mutableStateListOf<BluetoothDevice>()
    var isScanning by mutableStateOf(false)
    var isConnecting by mutableStateOf(false)
    var isConnected by mutableStateOf(AppGlobals.estaBrazaleteConectado)
    var statusText by mutableStateOf(if (AppGlobals.estaBrazaleteConectado) "Dispositivo Conectado" else "Desconectado")
    var feedbackColor by mutableStateOf(androidx.compose.ui.graphics.Color.Transparent)
    var feedbackText by mutableStateOf("")
    var showFeedback by mutableStateOf(false)

    init {
        loadUserProfile()
        loadNotes()
    }

    private fun loadUserProfile() {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("SesionUsuario", Context.MODE_PRIVATE)
        val correo = sharedPreferences.getString("correoUsuario", "") ?: ""
        if (correo.isNotEmpty()) {
            val datosPerfil = dbHelper.obtenerDatosPerfil(correo)
            userName = datosPerfil?.get("nombre") ?: "Usuario"
        }
    }

    private fun loadNotes() {
        try {
            notes.clear()
            notes.addAll(dbHelper.obtenerNotasMuestrario())
        } catch (e: Exception) {
            Log.e("MainMenuViewModel", "Error al cargar notas", e)
        }
    }

    fun playNote(nota: NoteModel) {
        try {
            hapticService.emitirTacton(nota)
            applyVisualFeedback(nota.nombre)
        } catch (e: SecurityException) {
            Log.e("MainMenuViewModel", "Permisos de Bluetooth denegados", e)
        }
    }

    private fun applyVisualFeedback(nombreNota: String) {
        val colorHex = when (nombreNota.split(" ")[0].uppercase()) {
            "DO" -> "#FF5722"; "RE" -> "#FFC107"; "MI" -> "#4CAF50"; "FA" -> "#2196F3"
            "SOL" -> "#9C27B0"; "LA" -> "#E91E63"; "SI" -> "#607D8B"
            else -> "#00D166"
        }
        feedbackColor = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorHex))
        feedbackText = "Tocaste la nota $nombreNota"
        showFeedback = true

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            showFeedback = false
        }, 1200)
    }

    private val scanTimeoutRunnable = Runnable { stopScan() }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null) {
            statusText = "Bluetooth no disponible"
            return
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            statusText = "Bluetooth Desactivado"
            return
        }

        if (isScanning) return

        scannedDevices.clear()
        
        // Add already bonded devices
        try {
            bluetoothAdapter!!.bondedDevices.forEach { device ->
                if (device.name != null && scannedDevices.none { it.address == device.address }) {
                    scannedDevices.add(device)
                }
            }
        } catch (e: Exception) {
            Log.e("MainMenuViewModel", "Error loading bonded devices", e)
        }

        isScanning = true
        statusText = "Buscando dispositivos..."
        
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            bluetoothAdapter!!.bluetoothLeScanner?.startScan(null, settings, leScanCallback)
            
            handler.removeCallbacks(scanTimeoutRunnable)
            handler.postDelayed(scanTimeoutRunnable, 10000)
            
        } catch (e: Exception) {
            isScanning = false
            statusText = "Error al iniciar escaneo"
            Log.e("MainMenuViewModel", "Scan error", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning) {
            isScanning = false
            bluetoothAdapter!!.bluetoothLeScanner?.stopScan(leScanCallback)
            handler.removeCallbacks(scanTimeoutRunnable)
            
            // Solo actualizamos el texto si no estamos intentando conectar
            if (!isConnecting) {
                statusText = if (isConnected) "Dispositivo Conectado" else "Búsqueda finalizada"
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // Verificamos el nombre tanto en el dispositivo como en el registro de escaneo (más fiable)
            val name = device.name ?: result.scanRecord?.deviceName
            if (name != null && scannedDevices.none { it.address == device.address }) {
                handler.post {
                    scannedDevices.add(device)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice, context: Context) {
        // Limpiar cualquier conexión previa antes de intentar una nueva
        try {
            AppGlobals.gattBluetooth?.disconnect()
            AppGlobals.gattBluetooth?.close()
        } catch (e: Exception) {
            Log.e("BLE_ERROR", "Error cerrando gatt previo", e)
        }
        
        stopScan()
        isConnecting = true
        statusText = "Conectando..."
        
        // Usamos autoConnect = false para una conexión inmediata solicitada por el usuario
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                // Si hay un error de estatus (ej: 133), cerramos y notificamos
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("BLE_ERROR", "Fallo en cambio de estado: status=$status")
                    handler.post {
                        statusText = "Error: Reintente ($status)"
                        isConnected = false
                        isConnecting = false
                        AppGlobals.estaBrazaleteConectado = false
                    }
                    gatt.close()
                    return
                }

                handler.post {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d("BLE_INFO", "¡Conectado a nivel GATT!")
                        statusText = "Dispositivo Conectado"
                        isConnected = true
                        isConnecting = false
                        AppGlobals.estaBrazaleteConectado = true
                        AppGlobals.gattBluetooth = gatt
                        
                        // Pequeño delay antes de buscar servicios suele mejorar la estabilidad en algunos dispositivos
                        handler.postDelayed({
                            gatt.discoverServices()
                        }, 600)
                        
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d("BLE_INFO", "Desconectado")
                        AppGlobals.gattBluetooth = null
                        AppGlobals.characteristicComandos = null
                        AppGlobals.estaBrazaleteConectado = false
                        isConnected = false
                        isConnecting = false
                        statusText = "Desconectado"
                        gatt.close()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(UUID.fromString(AppGlobals.SERVICE_UUID))
                    val char = service?.getCharacteristic(UUID.fromString(AppGlobals.COMMAND_UUID))
                    handler.post {
                        if (char != null) {
                            AppGlobals.characteristicComandos = char
                            statusText = "Brazalete listo para notas"
                            Toast.makeText(context, "¡Dispositivo vinculado con éxito!", Toast.LENGTH_SHORT).show()
                            Log.d("BLE_INFO", "Servicios y características listos")
                        } else {
                            Log.w("BLE_WARN", "Característica no encontrada")
                            statusText = "Conectado (Sin comandos)"
                        }
                    }
                } else {
                    Log.e("BLE_ERROR", "Error al descubrir servicios: $status")
                    handler.post { statusText = "Error en servicios" }
                }
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    override fun onCleared() {
        stopScan()
        dbHelper.close()
        handler.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
