package com.rommelguerra.prototipomusichaptic.integracion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** Una operación GATT a la vez. STOP reemplaza cualquier comando pendiente. */
@SuppressLint("MissingPermission")
class ConexionEntornoBle(private val context: Context) {
    private val servicio = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val uuidComando = UUID.fromString("abcdefab-1234-5678-1234-abcdefabcdef")
    private val handler = Handler(Looper.getMainLooper())
    val mensaje = MutableStateFlow("ESP32 desconectado · salida en el teléfono")
    val conectado = MutableStateFlow(false)
    val ocupado = MutableStateFlow(false)
    data class Dispositivo(val direccion: String, val nombre: String, val compatible: Boolean)
    val dispositivos = MutableStateFlow<List<Dispositivo>>(emptyList())
    val dispositivoConectado = MutableStateFlow<Dispositivo?>(null)
    val buscando = MutableStateFlow(false)
    private var gatt: BluetoothGatt? = null
    private var caracteristica: BluetoothGattCharacteristic? = null
    private var escaner: BluetoothLeScanner? = null
    private var escribiendo = false
    private var pendiente: String? = null
    private var generacion = 0
    private var escrituraId = 0
    fun permisosDenegados() { mensaje.value = "Permiso Bluetooth denegado. Puedes usar la vibración del teléfono." }

    private val callbackScan = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) { handler.post {
            if (escaner == null) return@post
            val compatible = result.scanRecord?.serviceUuids?.any { it.uuid == servicio } == true || result.scanRecord?.deviceName == "VIBRAFIL_ESP32"
            val item = Dispositivo(result.device.address, result.scanRecord?.deviceName ?: "Dispositivo BLE sin nombre", compatible)
            dispositivos.value = (dispositivos.value.filterNot { it.direccion == item.direccion } + item).sortedByDescending { it.compatible }
        } }
        override fun onScanFailed(code: Int) { handler.post { fallar("Falló la búsqueda BLE ($code)") } }
    }
    private val callbackGatt = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(device: BluetoothGatt, status: Int, state: Int) { handler.post {
            if (gatt !== device) { device.close(); return@post }
            if (status != BluetoothGatt.GATT_SUCCESS || state == BluetoothProfile.STATE_DISCONNECTED) {
                fallar("ESP32 desconectado · usa el teléfono"); return@post
            }
            if (state == BluetoothProfile.STATE_CONNECTED) {
                try { if (!device.discoverServices()) fallar("No se pudieron consultar los servicios BLE") }
                catch (e: SecurityException) { fallar("Se perdió el permiso Bluetooth") }
            }
        } }
        override fun onServicesDiscovered(device: BluetoothGatt, status: Int) { handler.post {
            if (gatt !== device) return@post
            val command = device.getService(servicio)?.getCharacteristic(uuidComando)
            if (status != BluetoothGatt.GATT_SUCCESS || command == null ||
                command.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
                fallar("Firmware incompatible: falta el servicio de comandos"); return@post
            }
            dispositivoConectado.value = dispositivos.value.firstOrNull { it.direccion == device.device.address }
            caracteristica = command; conectado.value = true; ocupado.value = false
            mensaje.value = "ESP32 conectado · selecciona el perfil del firmware instalado"
        } }
        override fun onCharacteristicWrite(device: BluetoothGatt, command: BluetoothGattCharacteristic, status: Int) { handler.post {
            if (gatt !== device || command.uuid != uuidComando) return@post
            escribiendo = false
            escrituraId++
            if (status != BluetoothGatt.GATT_SUCCESS) { fallar("Falló el envío al ESP32 ($status)"); return@post }
            mensaje.value = "Comando entregado al ESP32"
            val next = pendiente; pendiente = null
            if (next != null) enviar(next)
        } }
    }
    fun buscar() {
        if (conectado.value || ocupado.value) return
        dispositivos.value = emptyList()
        val permisos = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permisos.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            mensaje.value = "Faltan permisos para buscar dispositivos cercanos"; return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { mensaje.value = "Activa Bluetooth para conectar el ESP32"; return }
        try {
            escaner = adapter.bluetoothLeScanner
            checkNotNull(escaner) { "Escáner BLE no disponible" }
            buscando.value = true; ocupado.value = true; mensaje.value = "Buscando dispositivos cercanos…"
            escaner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callbackScan)
            val version = generacion
            handler.postDelayed({ if (version == generacion && buscando.value) { pararEscaneo(); ocupado.value = false; mensaje.value = "Búsqueda finalizada. Selecciona tu háptica o vuelve a buscar." } }, 12000)
        } catch (e: Exception) { fallar("No se pudo buscar: ${e.message}") }
    }
    fun conectar(direccion: String) {
        val item = dispositivos.value.firstOrNull { it.direccion == direccion } ?: return
        if (!item.compatible || conectado.value || (ocupado.value && !buscando.value)) return
        pararEscaneo(); generacion++; ocupado.value = true
        mensaje.value = "Conectando a ${item.nombre}…"
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            gatt = adapter?.getRemoteDevice(direccion)?.connectGatt(context, false, callbackGatt, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) { fallar("Bluetooth no disponible"); return }
            val version = generacion
            handler.postDelayed({ if (version == generacion && !conectado.value) fallar("La háptica no respondió. Vuelve a intentarlo.") }, 25000)
        } catch (_: Exception) { fallar("No se pudo conectar. Revisa Bluetooth y sus permisos.") }
    }
    fun enviar(comando: String): Boolean {
        val device = gatt ?: return false
        val command = caracteristica ?: return false
        if (!conectado.value) return false
        if (escribiendo) {
            if (comando == "STOP" || pendiente != "STOP") pendiente = comando
            return true
        }
        return try {
            escribiendo = true
            val bytes = comando.toByteArray(Charsets.UTF_8)
            val ok = if (Build.VERSION.SDK_INT >= 33) device.writeCharacteristic(command, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                else {
                    @Suppress("DEPRECATION")
                    command.value = bytes
                    command.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    device.writeCharacteristic(command)
                }
            if (!ok) { escribiendo = false; fallar("Android no pudo enviar el comando") }
            else {
                val version = generacion
                val id = ++escrituraId
                handler.postDelayed({ if (version == generacion && id == escrituraId && escribiendo) fallar("El ESP32 no confirmó la escritura") }, 4000)
            }
            ok
        } catch (e: Exception) { fallar("Error de envío: ${e.message}"); false }
    }
    private fun pararEscaneo() { try { escaner?.stopScan(callbackScan) } catch (_: SecurityException) {} ; escaner = null; buscando.value = false }
    private fun fallar(texto: String) { desconectar(); mensaje.value = texto }
    fun desconectar() {
        generacion++; pararEscaneo()
        dispositivoConectado.value = null
        conectado.value = false; ocupado.value = false; caracteristica = null
        pendiente = null; escribiendo = false
        val anterior = gatt; gatt = null
        try { anterior?.disconnect(); anterior?.close() } catch (_: SecurityException) {}
        mensaje.value = "ESP32 desconectado · salida en el teléfono"
    }
}
