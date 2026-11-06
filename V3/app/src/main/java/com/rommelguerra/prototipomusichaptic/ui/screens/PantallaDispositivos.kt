package com.rommelguerra.prototipomusichaptic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rommelguerra.prototipomusichaptic.integracion.IntegracionViewModel

@Composable
fun ResumenHaptica(conectado: Boolean, ocupado: Boolean, abrir: () -> Unit) {
    val color = when { conectado -> Color(0xFF14653C); ocupado -> Color(0xFF805000); else -> Color(0xFFA12622) }
    Card(onClick = abrir, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estado de conexión", fontWeight = FontWeight.Bold)
            Text(when { conectado -> "● Háptica conectada"; ocupado -> "◌ Buscando / conectando…"; else -> "○ Háptica desconectada" }, color = color, fontWeight = FontWeight.Bold)
            Text(if (conectado) "Servicio háptico verificado." else "Sin conexión háptica verificada. Los avisos disponibles usan el teléfono.", fontSize = 13.sp)
            Text("Ver dispositivos y conexión →", color = color)
        }
    }
}

@Composable
fun PantallaDispositivos(vm: IntegracionViewModel, onVolver: () -> Unit, onBuscar: () -> Unit) {
    val conectado by vm.bluetooth.conectado.collectAsState()
    val ocupado by vm.bluetooth.ocupado.collectAsState()
    val buscando by vm.bluetooth.buscando.collectAsState()
    val dispositivos by vm.bluetooth.dispositivos.collectAsState()
    val actual by vm.bluetooth.dispositivoConectado.collectAsState()
    val mensaje by vm.bluetooth.mensaje.collectAsState()
    TextButton(onClick = onVolver) { Text("← Volver a Inicio") }
    Text("Dispositivos", fontSize = 32.sp, fontWeight = FontWeight.Bold)
    Text("Busca tu háptica y selecciona Conectar. La app verifica su servicio de comandos antes de confirmar la conexión.")
    EstadoConexionHaptica(conectado, ocupado, vm.bluetooth::desconectar, onBuscar)
    Text("Dispositivo conectado", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    if (conectado) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(actual?.nombre ?: "Háptica ESP32", fontWeight = FontWeight.Bold)
                actual?.let { Text(it.direccion) }
                Text("Servicio háptico verificado", color = Color(0xFF14653C))
                OutlinedButton(onClick = { vm.detenerAviso(); vm.bluetooth.desconectar() }) { Text("Desconectar") }
            }
        }
    } else Text("Ninguna háptica conectada a esta aplicación.")
    Text("Dispositivos cercanos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Button(onClick = onBuscar, enabled = !ocupado && !conectado, modifier = Modifier.fillMaxWidth()) { Text("Buscar dispositivos") }
    if (ocupado) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        TextButton(onClick = vm.bluetooth::desconectar) { Text(if (buscando) "Cancelar búsqueda" else "Cancelar conexión") }
    }
    Text(mensaje)
    if (dispositivos.isEmpty() && !ocupado) Text("No hay dispositivos en la lista. Enciende la háptica y pulsa Buscar dispositivos.")
    dispositivos.filterNot { conectado && it.direccion == actual?.direccion }.forEach { dispositivo ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(dispositivo.nombre, fontWeight = FontWeight.Bold)
                Text(dispositivo.direccion, fontSize = 12.sp)
                Text(if (dispositivo.compatible) "Háptica anunciada · pendiente de verificar" else "Sin servicio háptico anunciado", fontSize = 13.sp)
                Button(enabled = dispositivo.compatible && !conectado && (!ocupado || buscando), onClick = { vm.bluetooth.conectar(dispositivo.direccion) }) { Text("Conectar") }
            }
        }
    }
    Text("Estado final de la háptica", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(when { conectado -> "● Conectada"; ocupado -> "◌ Conexión pendiente"; else -> "○ Desconectada" }, color = when { conectado -> Color(0xFF14653C); ocupado -> Color(0xFF805000); else -> Color(0xFFA12622) }, fontWeight = FontWeight.Bold)
}
