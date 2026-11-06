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

/** Pantalla unificada de los módulos de música y sonidos cotidianos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperienceScreen(integracion: IntegracionViewModel = viewModel()) {
    val estadoBle by integracion.bluetooth.mensaje.collectAsState()
    val conectadoBle by integracion.bluetooth.conectado.collectAsState()
    val ocupadoBle by integracion.bluetooth.ocupado.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    androidx.activity.compose.BackHandler(enabled = page != 0) { page = 0 }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Tu próxima canción") }
    var position by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var volume by remember { mutableStateOf(0.65f) }
    var loop by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Selecciona un audio para comenzar.") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    fun iniciarSiVisible() {
        if (page == 2 && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) integracion.iniciarEscucha()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        iniciarSiVisible()
    }
    fun prepararEscucha() {
        if (page != 2 || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            iniciarSiVisible()
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) prepararEscucha() else integracion.permisoDenegado()
    }

    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
        if (permisos.values.all { it } && page == 3 && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) integracion.conectarEsp32()
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
                next.setOnCompletionListener { playing = false; position = 0; status = "Reproducción finalizada." }
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
                playing = false; vibrator.cancel()
                integracion.detenerEscucha(); integracion.pausarSalida()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { player?.release(); vibrator.cancel(); integracion.detenerEscucha(); integracion.pausarSalida() } }
    LaunchedEffect(page) {
        if (page != 2) integracion.detenerEscucha()
        if (page != 1) integracion.detenerVistaPrevia()
    }
    LaunchedEffect(playing, player) {
        while (playing) {
            val nextPosition = player?.currentPosition ?: 0
            position = nextPosition
            delay(50)
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
            val scroll = androidx.compose.runtime.key(page) { rememberScrollState() }
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("HapticMusic", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Text("Siente lo que te conecta", color = Teal, fontSize = 13.sp) }
                    Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFFE0EBE4)) { Text("PISP · 05", Modifier.padding(12.dp), color = Teal, fontWeight = FontWeight.Bold) }
                }
                when(page) {
                    0 -> {
                        Text("Tu mundo,\na tu ritmo.", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
                        Text("Reproduce tu música y reconoce los sonidos disponibles en cada contexto.")
                        ExperienceCard("01 / EXPLORA", "Música", "Abre tu audio, reproduce y descubre cada ritmo.", Icons.Default.Headphones, Teal) { page = 1 }
                        ExperienceCard("02 / CONECTA", "Vida cotidiana", "Hogar, urbano, social e industrial: señales según tu contexto.", Icons.Default.Notifications, Color(0xFF985327)) { page = 2 }
                        ResumenHaptica(conectadoBle, ocupadoBle) { page = 3 }
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
                        Button(enabled = !integracion.analizando, onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Seleccionar música") }
                        Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()), onValueChange = { position = it.toInt(); player?.seekTo(position) }, enabled = ready, valueRange = 0f..duration.coerceAtLeast(1).toFloat())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(clock(position)); Text(clock(duration)) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = ready, onClick = { position = (position - 10000).coerceAtLeast(0); player?.seekTo(position) }) { Icon(Icons.Default.Replay10, "Retroceder diez segundos") }
                            FilledIconButton(enabled = ready, modifier = Modifier.size(72.dp), onClick = {
                                if (playing) { player?.pause(); integracion.pausarSalida() } else { integracion.detenerEscucha(); integracion.detenerVistaPrevia(); player?.start() }
                                playing = !playing
                            }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausar" else "Reproducir", Modifier.size(36.dp)) }
                            IconButton(enabled = ready, onClick = { position = (position + 10000).coerceAtMost(duration); player?.seekTo(position) }) { Icon(Icons.Default.Forward10, "Avanzar diez segundos") }
                            IconButton(enabled = ready, onClick = { player?.pause(); player?.seekTo(0); playing = false; position = 0; integracion.pausarSalida() }) { Icon(Icons.Default.Stop, "Detener") }
                        }
                        Text(status, color = Teal)
                        Text("Volumen · ${(volume * 100).toInt()} %", fontWeight = FontWeight.Bold)
                        Slider(value = volume, onValueChange = { volume = it; if (ready) player?.setVolume(it, it) })
                        Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = loop, onCheckedChange = { loop = it; if (ready) player?.isLooping = it }); Text("  Repetir canción") }
                        PanelMusicalEquipo(integracion, listo = ready, onAnalizar = {
                            player?.pause(); playing = false; player?.seekTo(0); position = 0
                            selectedUri?.let { integracion.analizar(it) }
                        }, onEscucharResultado = { file ->
                            if (ready) player?.pause()
                            playing = false
                            integracion.reproducirResultado(file)
                        })
                    }
                    3 -> {
                        PantallaDispositivos(integracion, onVolver = { page = 0 }, onBuscar = {
                            val permisos = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                            if (permisos.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) integracion.conectarEsp32()
                            else bluetoothPermission.launch(permisos)
                        })
                    }
                    2 -> {
                        PanelCotidianoEquipo(integracion, onEscuchar = {
                            if (integracion.escuchando) integracion.detenerEscucha() else {
                                if (ready) player?.pause()
                                playing = false
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) prepararEscucha()
                                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }, onConectar = { page = 3
                        }, onProbar = { clase ->
                            if (ready) player?.pause()
                            playing = false
                            integracion.probarCaso(clase)
                        })
                    }
                }
            }
        }
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








