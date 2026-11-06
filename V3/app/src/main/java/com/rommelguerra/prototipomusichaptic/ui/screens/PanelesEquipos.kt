package com.rommelguerra.prototipomusichaptic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rommelguerra.prototipomusichaptic.integracion.*
import com.tesis.audiotohaptics.config.Actuator
import java.io.File

@Composable
fun PanelCotidianoEquipo(vm: IntegracionViewModel, onEscuchar: () -> Unit, onConectar: () -> Unit, onProbar: (String) -> Unit) {
    val conectado by vm.bluetooth.conectado.collectAsState()
    val ocupado by vm.bluetooth.ocupado.collectAsState()
    Text("Sonidos cotidianos", fontSize = 32.sp, fontWeight = FontWeight.Bold)
    Text("Elige dónde estás. Los sonidos y sus prioridades cambian con el contexto.")
    vm.catalogo.contextos.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fila.forEach { contexto ->
                FilterChip(selected = vm.contexto == contexto, onClick = { vm.cambiarContexto(contexto) },
                    label = { Text(contexto.replaceFirstChar(Char::uppercaseChar)) },
                    modifier = Modifier.weight(1f).testTag("contexto_$contexto"))
            }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Micrófono del entorno", fontWeight = FontWeight.Bold)
                Text(if (vm.escuchando) "Activo · escuchando el entorno" else "Apagado · sin escucha", color = if (vm.escuchando) androidx.compose.ui.graphics.Color(0xFF14653C) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = vm.escuchando,
                onCheckedChange = { activar -> if (activar != vm.escuchando) onEscuchar() },
                enabled = vm.escuchando || !vm.analizando,
                modifier = Modifier.testTag("microfono_entorno").semantics { contentDescription = "Escucha del entorno con micrófono" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                    checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF14653C),
                    uncheckedThumbColor = androidx.compose.ui.graphics.Color(0xFF59616B),
                    uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFFE1E4E8)
                )
            )
        }
    }
    Text(vm.mensajeCotidiano)
    vm.ultimaDeteccion?.let { evento ->
        AvisoSonido(
            clase = evento.clase,
            encabezado = "Última detección",
            detalle = "${(evento.confianza * 100).toInt()} % de confianza · prioridad ${evento.prioridad} · ${evento.contexto}"
        )
    }
    Text("El nivel de sonido del motor está sin calibrar para este teléfono.", fontSize = 13.sp)
    ResumenHaptica(conectado, ocupado, onConectar)
    Text("Configuración de avisos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("Elige el firmware instalado en tu háptica.", fontSize = 13.sp)
    PerfilEntorno.entries.forEach { perfil ->
        FilterChip(selected = vm.perfil == perfil, onClick = { vm.cambiarPerfil(perfil) }, label = { Text(perfil.etiqueta) })
    }
    OutlinedButton(onClick = { vm.detenerAviso() }) { Text("Detener señal") }
    Text("Intensidad del teléfono · ${(vm.intensidad * 100).toInt()} %", fontWeight = FontWeight.Bold)
    Slider(value = vm.intensidad, onValueChange = { vm.intensidad = it }, valueRange = 0.1f..1f)
    Text("La intensidad de la háptica depende de su firmware.", fontSize = 13.sp)
    Text(vm.mensajeSalida)
    Text("Sonidos en ${vm.contexto}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    vm.casos.forEach { caso ->
        OutlinedCard(Modifier.fillMaxWidth().testTag("caso_${caso.clase}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(CatalogoCotidianoEquipo.etiqueta(caso.clase), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (caso.entrenado) {
                    Text("Disponible · prioridad ${caso.prioridad} · confianza mínima ${(caso.umbral * 100).toInt()} %")
                    if (CatalogoCotidianoEquipo.sonido(caso.clase) == null) {
                        Text("Detección en pantalla. El firmware recibido no incluye una señal para este sonido.", fontSize = 13.sp)
                    }
                    TextButton(onClick = { onProbar(caso.clase) }, modifier = Modifier.testTag("probar_${caso.clase}")) {
                        Text(if (CatalogoCotidianoEquipo.sonido(caso.clase) == null) "Probar aviso visual" else "Probar aviso y vibración")
                    }
                    if (vm.pruebaVisual == caso.clase) {
                        AvisoSonido(caso.clase, "Demostración manual", "Vista previa del aviso")
                    }
                } else {
                    Text("Pendiente de entrenamiento", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Está definido en este contexto, pero el modelo recibido todavía no lo reconoce.", fontSize = 13.sp)
                }
            }
        }
    }
    Text("Las demostraciones manuales quedan fuera del historial de detecciones.", fontSize = 13.sp)
    if (vm.historial.isNotEmpty()) {
        Text("Detecciones de esta sesión", fontWeight = FontWeight.Bold)
        vm.historial.take(8).forEach { Text(it) }
    }
}

@Composable
private fun AvisoSonido(clase: String, encabezado: String, detalle: String) {
    val (icono, color) = when (clase) {
        "ladrido" -> Icons.Default.Pets to Color(0xFF7A4700)
        "llanto_bebe" -> Icons.Default.ChildCare to Color(0xFF6A348C)
        "vidrio_roto" -> Icons.Default.Warning to Color(0xFF18588B)
        "sirena" -> Icons.Default.Campaign to Color(0xFFA5252B)
        "claxon" -> Icons.Default.DirectionsCar to Color(0xFF17614B)
        "maquinaria" -> Icons.Default.PrecisionManufacturing to Color(0xFF454D63)
        else -> Icons.Default.ReportProblem to Color(0xFF3F4752)
    }
    val titulo = when (clase) {
        "ladrido" -> "Ladrido de perro"
        "sirena" -> "Sirena de emergencia"
        else -> CatalogoCotidianoEquipo.etiqueta(clase)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("aviso_$clase").semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = color,
        contentColor = Color.White
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text(encabezado, style = MaterialTheme.typography.labelLarge)
                Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(detalle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PanelMusicalEquipo(vm: IntegracionViewModel, listo: Boolean, onAnalizar: () -> Unit, onEscucharResultado: (File) -> Unit) {
    HorizontalDivider()
    Text("Audio a señales hápticas", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("La biblioteca separa voz, guitarra y percusión, y genera un WAV sincronizado para cada canal.")
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estado de los modelos", fontWeight = FontWeight.Bold)
            Text("Basic Pitch: ${if (vm.basicPitchInstalado) "incluido" else "no incluido"}")
            Text("Demucs: ${if (vm.demucsInstalado) "incluido" else "no incluido"}")
            if (!vm.demucsInstalado) Text("El análisis requiere el modelo Demucs que falta. No se descarga ni se genera desde la app.")
        }
    }
    Row {
        Checkbox(checked = vm.usarValoresPrueba, enabled = !vm.analizando, onCheckedChange = { vm.usarValoresPrueba = it })
        Text("Usar configuración de prueba: 150/200/250 Hz y 44,1 kHz. Son valores del equipo sin confirmar para los actuadores.", Modifier.padding(top = 8.dp))
    }
    Row {
        Checkbox(checked = vm.exportarFuentes, enabled = !vm.analizando, onCheckedChange = { vm.exportarFuentes = it })
        Text("Guardar también voz, guitarra y batería separadas", Modifier.padding(top = 8.dp))
    }
    if (!vm.usarValoresPrueba) Text("La configuración oficial de los actuadores está pendiente.", fontSize = 13.sp)
    Button(enabled = listo && vm.musicaDisponible && vm.usarValoresPrueba && !vm.analizando,
        onClick = onAnalizar, modifier = Modifier.fillMaxWidth()) { Text("Analizar música") }
    if (vm.analizando) {
        LinearProgressIndicator(progress = vm.progresoMusica, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { vm.cancelarAnalisis() }) { Text("Cancelar análisis") }
    }
    Text(vm.mensajeMusica)
    Text("Los WAV representan ritmo y duración con una portadora fija por canal. Esta integración no transmite los tres canales musicales al ESP32.", fontSize = 13.sp)
    vm.resultadoMusica?.let { result ->
        Text("Resultados del análisis", fontWeight = FontWeight.Bold)
        Text("Duración: ${"%.1f".format(result.durationS)} s · configuración ${if (result.configConfirmed) "confirmada" else "de prueba"}")
        result.outputs.forEach { (actuador, output) ->
            val nombre = when (actuador) { Actuator.VOICE -> "Voz"; Actuator.GUITAR -> "Guitarra"; Actuator.PERCUSSION -> "Percusión" }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(nombre, fontWeight = FontWeight.Bold)
                    Text("${output.eventCount} eventos · ${output.carrierHz} Hz · ${output.status}")
                    Text("WAV: ${output.wavFile.name}")
                    output.midiFile?.let { Text("MIDI: ${it.name}") }
                    Text("Eventos: ${output.eventsFile.name}")
                    TextButton(onClick = { onEscucharResultado(output.wavFile) }) { Text("Escuchar señal de $nombre") }
                }
            }
        }
        result.stemFiles.forEach { (nombre, file) -> TextButton(onClick = { onEscucharResultado(file) }) { Text("Escuchar fuente: $nombre") } }
        Text("Resultados guardados en: ${result.outputDirectory}", fontSize = 12.sp)
        result.warnings.forEach { Text(it, fontSize = 13.sp) }
    }
    if (vm.mensajeVistaPrevia.isNotBlank()) {
        Text(vm.mensajeVistaPrevia)
        OutlinedButton(onClick = { vm.detenerVistaPrevia() }) { Text("Detener vista previa") }
    }
}
