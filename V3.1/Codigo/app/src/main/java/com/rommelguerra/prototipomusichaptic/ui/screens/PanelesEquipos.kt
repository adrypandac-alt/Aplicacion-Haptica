package com.rommelguerra.prototipomusichaptic.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
    val activo by remember(vm) { derivedStateOf { vm.nivelMicrofono != null } }
    val casos = remember(vm, vm.contexto) { vm.casos }
    Text("Sonidos cotidianos", fontSize = 32.sp, fontWeight = FontWeight.Bold)
    Text("Elige un contexto para escuchar y probar sus avisos.")
    vm.catalogo.contextos.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fila.forEach { contexto ->
                FilterChip(selected = vm.contexto == contexto, onClick = { vm.cambiarContexto(contexto) },
                    label = { Text(contexto.replaceFirstChar(Char::uppercaseChar)) },
                    leadingIcon = {
                        val icono = when (contexto) {
                            "hogar" -> Icons.Default.Home
                            "urbano" -> Icons.Default.LocationCity
                            "industrial" -> Icons.Default.Factory
                            else -> Icons.Default.Groups
                        }
                        Icon(icono, null, Modifier.size(FilterChipDefaults.IconSize))
                    },
                    modifier = Modifier.weight(1f).testTag("contexto_$contexto"))
            }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Micrófono del entorno", fontWeight = FontWeight.Bold)
                Text(when {
                    activo -> "Activo · recibiendo audio"
                    vm.escuchando -> "Esperando las primeras muestras…"
                    else -> "Apagado · sin escucha"
                })
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
        MedidorMicrofono(
            nivel = { vm.nivelMicrofono },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        )
    }
    Text(vm.mensajeCotidiano)
    vm.ultimaDeteccion?.let { evento ->
        AvisoSonido(
            clase = evento.clase,
            encabezado = "Última detección",
            detalle = "${(evento.confianza * 100).toInt()} % de confianza · prioridad ${evento.prioridad} · ${evento.contexto}"
        )
    }
    if (vm.escuchando && !vm.notificacionesActivas) {
        Text("Las notificaciones están desactivadas. Puedes activarlas en los ajustes de Android.", fontSize = 13.sp)
    }
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
    casos.forEach { caso ->
        val (_, acento) = estiloSonido(caso.clase)
        OutlinedCard(Modifier.fillMaxWidth().testTag("caso_${caso.clase}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconoSonido(caso.clase, 40.dp, 22.dp)
                    Text(CatalogoCotidianoEquipo.etiqueta(caso.clase), color = acento, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (caso.entrenado) {
                    Text("Disponible · prioridad ${caso.prioridad} · confianza mínima ${(caso.umbral * 100).toInt()} %")
                    if (CatalogoCotidianoEquipo.sonido(caso.clase) == null) {
                        Text("Aviso en el teléfono. El firmware recibido no incluye una señal para este sonido.", fontSize = 13.sp)
                    }
                    TextButton(onClick = { onProbar(caso.clase) }, modifier = Modifier.testTag("probar_${caso.clase}"), colors = ButtonDefaults.textButtonColors(contentColor = acento)) {
                        Text(if (CatalogoCotidianoEquipo.sonido(caso.clase) == null) "Probar aviso visual" else "Probar aviso y vibración")
                    }
                    if (vm.pruebaVisual == caso.clase) {
                        AvisoSonido(caso.clase, "Demostración manual", "Vista previa del aviso")
                    }
                } else {
                    Text("Pendiente de entrenamiento", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private val alturasMedidor = listOf(0.50f, 0.85f, 0.65f, 1f, 0.75f, 0.95f, 0.55f, 0.80f, 0.60f)

@Composable
private fun MedidorMicrofono(nivel: () -> Float?, modifier: Modifier = Modifier) {
    val activo = MaterialTheme.colorScheme.primary
    val inactivo = MaterialTheme.colorScheme.outline
    Canvas(modifier.height(28.dp).testTag("nivel_microfono").semantics {
        contentDescription = "Actividad del micrófono"
    }) {
        val muestra = nivel()
        val amplitud = ((muestra ?: -70f).coerceIn(-70f, 0f) + 70f) / 70f
        val ancho = 5.dp.toPx()
        val paso = minOf(14.dp.toPx(), size.width / alturasMedidor.size)
        val centro = alturasMedidor.lastIndex / 2f
        alturasMedidor.forEachIndexed { i, proporcion ->
            val alto = ancho + (size.height - ancho) * amplitud * proporcion
            drawRoundRect(
                color = if (muestra == null) inactivo else activo,
                topLeft = Offset(size.width / 2f + (i - centro) * paso - ancho / 2f, (size.height - alto) / 2f),
                size = Size(ancho, alto),
                cornerRadius = CornerRadius(ancho / 2f)
            )
        }
    }
}

private fun estiloSonido(clase: String) = when (clase) {
    "ladrido" -> Icons.Default.Pets to Color(0xFF7A4700)
    "llanto_bebe" -> Icons.Default.ChildCare to Color(0xFF6A348C)
    "vidrio_roto" -> Icons.Default.Warning to Color(0xFF18588B)
    "sirena" -> Icons.Default.Campaign to Color(0xFFA5252B)
    "claxon" -> Icons.Default.DirectionsCar to Color(0xFF17614B)
    "maquinaria" -> Icons.Default.PrecisionManufacturing to Color(0xFF454D63)
    else -> Icons.Default.ReportProblem to Color(0xFF3F4752)
}

@Composable
private fun IconoSonido(clase: String, lado: Dp, tamanoIcono: Dp) {
    val (icono, color) = estiloSonido(clase)
    Surface(Modifier.size(lado), shape = MaterialTheme.shapes.small, color = color, contentColor = Color.White) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icono, null, Modifier.size(tamanoIcono))
        }
    }
}

@Composable
private fun AvisoSonido(clase: String, encabezado: String, detalle: String) {
    val titulo = when (clase) {
        "ladrido" -> "Ladrido de perro"
        "sirena" -> "Sirena de emergencia"
        else -> CatalogoCotidianoEquipo.etiqueta(clase)
    }
    OutlinedCard(
        Modifier.fillMaxWidth().testTag("aviso_$clase").semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconoSonido(clase, 56.dp, 30.dp)
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
