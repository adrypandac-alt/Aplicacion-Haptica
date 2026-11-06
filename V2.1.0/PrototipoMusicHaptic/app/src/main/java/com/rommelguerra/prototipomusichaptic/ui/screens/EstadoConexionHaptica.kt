package com.rommelguerra.prototipomusichaptic.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** El estado de la háptica procede del servicio GATT validado, no del emparejamiento. */
@Composable
fun EstadoConexionHaptica(
    conectado: Boolean,
    conectando: Boolean,
    alApagar: () -> Unit,
    abrirConexion: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var activado by remember { mutableStateOf<Boolean?>(null) }
    val apagarActual by rememberUpdatedState(alApagar)
    fun actualizar() {
        activado = if (adapter == null) false else try {
            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) null
            else adapter.isEnabled
        } catch (_: SecurityException) { null }
        if (activado == false) apagarActual()
    }
    val activar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { actualizar() }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        actualizar()
        if (granted && activado == false && adapter != null) activar.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) actualizar()
            }
        }
        // Los cambios Bluetooth provienen también del proceso privilegiado del sistema.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) actualizar() }
        lifecycle.addObserver(observer)
        actualizar()
        onDispose { context.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Estado de conexión", fontWeight = FontWeight.Bold)
            Text("Bluetooth: " + when {
                adapter == null -> "No disponible"
                activado == null -> "Permiso pendiente"
                activado == true -> "Activado"
                else -> "Desactivado"
            })
            Text("Háptica: " + when {
                activado != true -> "Desconectada"
                conectado -> "Conectada"
                conectando -> "Conectando…"
                else -> "Desconectada"
            }, color = if (activado == true && conectado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = adapter != null, onClick = {
                when (activado) {
                    null -> if (Build.VERSION.SDK_INT >= 31) permiso.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    false -> activar.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    true -> abrirConexion()
                }
            }) {
                Text(when {
                    adapter == null -> "Bluetooth no disponible"
                    activado == null -> "Permitir Bluetooth"
                    activado == false -> "Activar Bluetooth"
                    conectado -> "Administrar háptica"
                    else -> "Conectar háptica"
                })
            }
        }
    }
}
