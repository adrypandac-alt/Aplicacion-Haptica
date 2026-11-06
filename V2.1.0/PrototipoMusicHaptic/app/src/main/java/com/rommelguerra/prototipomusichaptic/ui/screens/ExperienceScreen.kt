package com.rommelguerra.prototipomusichaptic.ui.screens

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.media.MediaPlayer


import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rommelguerra.prototipomusichaptic.integracion.*

private val Ink = Color(0xFF18283A)
private val Teal = Color(0xFF096B60)
private val Mist = Color(0xFFF3F6F3)

/** Entry point for the two experiences. Device BLE learning remains in the original module. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperienceScreen(integracion: IntegracionViewModel = viewModel()) {
    val estadoBle by integracion.bluetooth.mensaje.collectAsState()
    val conectadoBle by integracion.bluetooth.conectado.collectAsState()
    val ocupadoBle by integracion.bluetooth.ocupado.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var page by remember { mutableStateOf(0) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Tu próxima canción") }
    var position by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var volume by remember { mutableStateOf(0.65f) }
    var intensity by remember { mutableStateOf(0.5f) }
    var loop by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Selecciona un audio para comenzar.") }
    var alert by remember { mutableStateOf<String?>(null) }
    var pulses by remember { mutableStateOf(longArrayOf(0)) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && page == 2 && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) integracion.iniciarEscucha()
        else if (!granted) integracion.permisoDenegado()
    }

    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
        if (permisos.values.all { it } && page == 2 && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) integracion.conectarEsp32()
        else if (permisos.values.any { !it }) integracion.bluetooth.permisosDenegados()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            integracion.limpiarMusica()
            playing = false; ready = false; position = 0; duration = 0
            player?.release(); player = null
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    title = if (it.moveToFirst()) it.getString(0) else "Audio seleccionado"
                }
                val next = MediaPlayer()
                player = next
                next.setDataSource(context, uri)
                next.setOnPreparedListener {
                    duration = it.duration; ready = true
                    it.setVolume(volume, volume); it.isLooping = loop
                    status = "Audio listo · pulsa reproducir."
                }
                next.setOnCompletionListener { playing = false; position = 0; integracion.reposicionar(0); status = "Reproducción finalizada." }
                next.setOnErrorListener { _, _, _ ->
                    playing = false; ready = false; status = "No se pudo reproducir este formato. Selecciona otro audio."; true
                }
                status = "Preparando audio…"
                next.prepareAsync()
            }.onFailure { player?.release(); player = null; status = "No se pudo abrir el archivo. Intenta con otro audio." }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (ready) player?.pause()
                playing = false; vibrator.cancel(); alert = null
                integracion.detenerEscucha(); integracion.pausarSalida()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { player?.release(); vibrator.cancel(); integracion.detenerEscucha(); integracion.pausarSalida() } }
    LaunchedEffect(intensity) { integracion.intensidad = intensity }
    LaunchedEffect(page) { if (page != 2) integracion.detenerEscucha() }
    LaunchedEffect(playing, player) {
        while (playing) {
            val nextPosition = player?.currentPosition ?: 0
            if (nextPosition < position) integracion.reposicionar(nextPosition)
            position = nextPosition
            integracion.sincronizar(position)
            delay(50)
        }
    }
    LaunchedEffect(alert) {
        if (alert != null) {
            delay(pulses.sum()); alert = null
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Teal, secondaryContainer = Color(0xFFE0EBE4), onSecondaryContainer = Teal, background = Mist, surface = Color.White, onSurface = Ink)) {
        Scaffold(containerColor = Mist, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Inicio", "Música", "Cotidiano").forEachIndexed { i, label ->
                    NavigationBarItem(selected = page == i, onClick = { page = i },
                        icon = { Icon(when(i) { 0 -> Icons.Default.Home; 1 -> Icons.Default.MusicNote; else -> Icons.Default.Notifications }, label) }, label = { Text(label) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("HapticMusic", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Text("Siente lo que te conecta", color = Teal, fontSize = 13.sp) }
                    Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFFE0EBE4)) { Text("PISP · 05", Modifier.padding(12.dp), color = Teal, fontWeight = FontWeight.Bold) }
                }
                when(page) {
                    0 -> {
                        Text("Tu mundo,\na tu ritmo.", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
                        Text("Explora la música y reconoce señales cotidianas a través de experiencias visuales y táctiles.")
                        ExperienceCard("01 / EXPLORA", "Música", "Abre tu audio, reproduce y descubre cada ritmo.", Icons.Default.Headphones, Teal) { page = 1 }
                        ExperienceCard("02 / CONECTA", "Vida cotidiana", "Reconoce perro, bebé, sirenas, bocinas y alarmas.", Icons.Default.Notifications, Color(0xFF985327)) { page = 2 }
                        EstadoConexionHaptica(conectadoBle, ocupadoBle, integracion.bluetooth::desconectar) { page = 2 }
                        Text("Diseñado para explorar sin depender solo del sonido.", color = Teal)
                    }
                    1 -> {
                        Text("Siente la música", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("Tu biblioteca, tu momento. Elige un archivo del teléfono.")
                        Surface(shape = RoundedCornerShape(28.dp), color = Ink) {
                            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Icon(Icons.Default.GraphicEq, null, tint = Color(0xFFACDEB7), modifier = Modifier.size(96.dp))
                                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(if (playing) "REPRODUCIENDO" else "TU ESPACIO MUSICAL", color = Color(0xFFACDEB7), fontSize = 12.sp)
                            }
                        }
                        Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Seleccionar música") }
                        Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()), onValueChange = { position = it.toInt(); player?.seekTo(position); integracion.reposicionar(position) }, enabled = ready, valueRange = 0f..duration.coerceAtLeast(1).toFloat())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(clock(position)); Text(clock(duration)) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = ready, onClick = { position = (position - 10000).coerceAtLeast(0); player?.seekTo(position); integracion.reposicionar(position) }) { Icon(Icons.Default.Replay10, "Retroceder diez segundos") }
                            FilledIconButton(enabled = ready, modifier = Modifier.size(72.dp), onClick = {
                                if (playing) { player?.pause(); integracion.pausarSalida() } else { integracion.detenerEscucha(); player?.start() }
                                playing = !playing
                            }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausar" else "Reproducir", Modifier.size(36.dp)) }
                            IconButton(enabled = ready, onClick = { position = (position + 10000).coerceAtMost(duration); player?.seekTo(position); integracion.reposicionar(position) }) { Icon(Icons.Default.Forward10, "Avanzar diez segundos") }
                            IconButton(enabled = ready, onClick = { player?.pause(); player?.seekTo(0); playing = false; position = 0; integracion.reposicionar(0) }) { Icon(Icons.Default.Stop, "Detener") }
                        }
                        Text(status, color = Teal)
                        Text("Volumen · ${(volume * 100).toInt()} %", fontWeight = FontWeight.Bold)
                        Slider(value = volume, onValueChange = { volume = it; if (ready) player?.setVolume(it, it) })
                        Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = loop, onCheckedChange = { loop = it; if (ready) player?.isLooping = it }); Text("  Repetir canción") }
                        OutlinedButton(enabled = ready && integracion.musicaDisponible && !integracion.analizando, onClick = {
                            player?.pause(); playing = false; player?.seekTo(0); position = 0
                            selectedUri?.let { integracion.analizar(it) }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (integracion.analizando) "Analizando…" else "Analizar música") }
                        Text(integracion.mensajeMusica, color = Teal)
                        Text("Las señales del nuevo panel usan la vibración del teléfono. Conecta el dispositivo háptico desde Vida cotidiana.", fontSize = 13.sp)
                    }
                    2 -> {
                        Text("Conecta con tu día", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("Elige los sonidos que quieres reconocer.")
                        PerfilEntorno.entries.forEach { perfil ->
                            FilterChip(selected = integracion.perfil == perfil, onClick = { alert = null; integracion.cambiarPerfil(perfil) }, label = { Text(perfil.etiqueta) })
                        }
                        Text(if (integracion.perfil == PerfilEntorno.SOCIAL) "Reconoce perro y bebé. Nombre propio disponible como prueba manual." else "Reconoce sirenas, bocinas y alarmas a través del micrófono.")
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Dispositivo de salida", fontWeight = FontWeight.Bold)
                                Text(estadoBle)
                                Text("Selecciona el perfil que corresponde a tu dispositivo.", fontSize = 12.sp)
                                Button(enabled = !ocupadoBle, onClick = {
                                    if (conectadoBle) { integracion.detenerAviso(); integracion.bluetooth.desconectar() }
                                    else {
                                        val permisos = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                        if (permisos.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) integracion.conectarEsp32()
                                        else bluetoothPermission.launch(permisos)
                                    }
                                }) { Text(if (conectadoBle) "Desconectar ESP32" else if (ocupadoBle) "Conectando…" else "Conectar ESP32") }
                                if (ocupadoBle) TextButton(onClick = { integracion.bluetooth.desconectar() }) { Text("Cancelar búsqueda") }
                                OutlinedButton(onClick = { integracion.detenerAviso(); alert = null }) { Text("Detener señal") }
                            }
                        }
                        Button(enabled = integracion.cotidianoDisponible, modifier = Modifier.fillMaxWidth(), onClick = {
                            if (integracion.escuchando) integracion.detenerEscucha() else {
                                if (ready) player?.pause()
                                playing = false
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) integracion.iniciarEscucha()
                                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) { Text(if (integracion.escuchando) "Detener escucha" else "Iniciar escucha del entorno") }
                        Text(integracion.mensajeCotidiano, color = Teal)
                        if (integracion.ultimaDeteccion.isNotBlank()) Text("Última detección: ${integracion.ultimaDeteccion}", fontWeight = FontWeight.Bold)
                        Surface(color = Color(0xFFFFEAD4), shape = RoundedCornerShape(18.dp)) { Text("Pruebas manuales · los botones de abajo son demostraciones, no detecciones del entorno.", Modifier.padding(18.dp), color = Color(0xFF71471D)) }
                        Text("Intensidad del teléfono · ${(intensity * 100).toInt()} %", fontWeight = FontWeight.Bold)
                        Text("La intensidad del brazalete se configura en el propio dispositivo.", fontSize = 12.sp)
                        Slider(value = intensity, onValueChange = { intensity = it }, valueRange = 0.1f..1f)
                        CatalogoEntorno.sonidos(integracion.perfil).forEach { sonido ->
                            OutlinedCard(onClick = {
                                if (ready) player?.pause()
                                playing = false
                                integracion.probar(sonido)
                                pulses = CatalogoEntorno.patron(sonido).tiemposMs.toLongArray()
                                alert = sonido.etiqueta
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(sonido.etiqueta, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        Text(if (sonido == SonidoCotidiano.NOMBRE) "Prueba manual del nombre propio" else "Toca para sentir esta señal")
                                    }
                                    Icon(Icons.Default.TouchApp, "Probar ${sonido.etiqueta}")
                                }
                            }
                        }
                        Text(integracion.mensajeSalida, color = Teal)
                        if (integracion.historial.isNotEmpty()) {
                            Text("Detecciones de esta sesión", fontWeight = FontWeight.Bold)
                            integracion.historial.take(8).forEach { Text(it) }
                        }
                        Text(if (vibrator.hasVibrator()) "La vibración de esta demo utiliza el teléfono." else "Este dispositivo no tiene vibrador. Puedes ver las señales en pantalla.", fontSize = 13.sp)
                    }
                }
            }
        }
        if (alert != null) AlertDialog(onDismissRequest = { integracion.detenerAviso(); alert = null }, icon = { Icon(Icons.Default.Vibration, null) }, title = { Text(alert!!) }, text = { Text("Señal de demostración activa") }, confirmButton = { TextButton(onClick = { integracion.detenerAviso(); alert = null }) { Text("Detener") } })
    }
}

private fun clock(ms: Int): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExperienceCard(eyebrow: String, title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(eyebrow, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp); Icon(icon, null, tint = Color.White) }
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color.White)
            Icon(Icons.Default.ArrowForward, "Abrir $title", tint = Color.White)
        }
    }
}





