package com.rommelguerra.prototipomusichaptic.integracion

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.indoamerica.haptica.soundengine.SoundEngine
import com.indoamerica.haptica.soundengine.SoundEvent
import com.tesis.audiotohaptics.AudioToHaptics
import com.tesis.audiotohaptics.ProcessingResult
import com.tesis.audiotohaptics.config.HapticsConfig
import com.tesis.audiotohaptics.config.HapticsException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Conecta las API de los equipos con la app, sin cambiar clasificación ni síntesis. */
class IntegracionViewModel(application: Application) : AndroidViewModel(application) {
    val bluetooth = ConexionEntornoBle(application)
    val catalogo = CatalogoCotidianoEquipo(application)
    private val salida: SalidaHaptica = SalidaHapticaTelefono(application)
    private val avisosTelefono = AvisosSonidoAndroid(application)
    var notificacionesActivas by mutableStateOf(avisosTelefono.habilitadas())
        private set
    var nivelMicrofono by mutableStateOf<Float?>(null)
        private set
    private var escucha: Job? = null
    private val exclusividadEscucha = Mutex()
    private var versionEscucha = 0
    var contexto by mutableStateOf("hogar")
        private set
    // Perfil de firmware: es independiente del contexto acústico.
    var perfil by mutableStateOf(PerfilEntorno.SOCIAL)
        private set
    var historial by mutableStateOf(listOf<String>())
        private set
    var mensajeSalida by mutableStateOf("Salida: teléfono para los patrones disponibles")
        private set
    var mensajeCotidiano by mutableStateOf("Selecciona un contexto e inicia la escucha.")
        private set
    var escuchando by mutableStateOf(false)
        private set
    var ultimaDeteccion by mutableStateOf<SoundEvent?>(null)
        private set
    var pruebaVisual by mutableStateOf<String?>(null)
        private set
    var intensidad by mutableStateOf(0.5f)
    val cotidianoDisponible = true
    val casos get() = catalogo.casos(contexto)

    private val estadoModelos = RegistroAlgoritmos.crearMusica(application, HapticsConfig.default()).modelStatus()
    val demucsInstalado = estadoModelos.demucsInstalled
    val basicPitchInstalado = estadoModelos.basicPitchInstalled
    val musicaDisponible = estadoModelos.ready
    var usarValoresPrueba by mutableStateOf(false)
    var exportarFuentes by mutableStateOf(false)
    var resultadoMusica by mutableStateOf<ProcessingResult?>(null)
        private set
    var progresoMusica by mutableStateOf(0f)
        private set
    var analizando by mutableStateOf(false)
        private set
    var mensajeMusica by mutableStateOf(estadoMusicalInicial())
        private set
    var mensajeVistaPrevia by mutableStateOf("")
        private set
    private var analisis: Job? = null
    private var versionAnalisis = 0
    private val exclusividadAnalisis = Mutex()
    private var vistaPrevia: MediaPlayer? = null

    private fun estadoMusicalInicial() = if (musicaDisponible)
        "Biblioteca AudioToHaptics lista. Selecciona un audio y una configuración."
        else "Biblioteca musical integrada. Falta htdemucs_6s.onnx; el análisis completo está pendiente. Puedes reproducir tu música."

    fun cambiarContexto(nuevo: String) {
        require(nuevo in catalogo.contextos)
        if (nuevo == contexto) return
        detenerEscucha()
        contexto = nuevo
        ultimaDeteccion = null
        mensajeCotidiano = "Contexto ${nuevo.replaceFirstChar(Char::uppercaseChar)}. Pulsa Iniciar escucha."
    }
    fun cambiarPerfil(nuevo: PerfilEntorno) { detenerAviso(); perfil = nuevo }
    fun conectarEsp32() = bluetooth.buscar()
    fun detenerAviso() {
        pruebaVisual = null
        salida.detener()
        bluetooth.enviar("STOP")
    }

    private fun emitir(clase: String) {
        salida.detener()
        val sonido = CatalogoCotidianoEquipo.sonido(clase)
        if (sonido == null) {
            mensajeSalida = "${CatalogoCotidianoEquipo.etiqueta(clase)}: aviso en pantalla. No hay patrón ni comando en el firmware recibido."
            return
        }
        val compatible = sonido in CatalogoEntorno.sonidos(perfil)
        val enviado = compatible && bluetooth.enviar(CatalogoEntorno.comando(sonido))
        mensajeSalida = if (enviado) "Comando enviado al ESP32 · ${perfil.etiqueta}"
            else if (bluetooth.conectado.value && !compatible) "Patrón en el teléfono: el firmware seleccionado no admite esta alerta."
            else "Patrón en el teléfono · háptica no conectada"
        if (!enviado) salida.reproducir(CatalogoEntorno.patron(sonido), intensidad)
    }
    fun probarCaso(clase: String) {
        if (casos.none { it.clase == clase && it.entrenado }) return
        detenerEscucha()
        pruebaVisual = clase
        emitir(clase)
    }

    fun iniciarEscucha() {
        if (escuchando || analizando) return
        detenerVistaPrevia()
        detenerAviso()
        ultimaDeteccion = null
        escuchando = true
        notificacionesActivas = avisosTelefono.habilitadas()
        mensajeCotidiano = "Preparando reconocimiento…"
        val seleccionado = contexto
        val version = ++versionEscucha
        escucha = viewModelScope.launch {
            try {
                exclusividadEscucha.withLock {
                    var creado: SoundEngine? = null
                    try {
                        // Conservar la referencia incluso si se cancela durante la carga nativa.
                        withContext(NonCancellable + Dispatchers.Default) { creado = RegistroAlgoritmos.crearCotidiano(getApplication()) }
                        ensureActive()
                        val next = checkNotNull(creado)
                        next.setContext(seleccionado)
                        coroutineScope {
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                next.events.collect { e ->
                                    if (version != versionEscucha) return@collect
                                    ultimaDeteccion = e
                                    val resumen = "${CatalogoCotidianoEquipo.etiqueta(e.clase)} · ${(e.confianza * 100).toInt()} % · prioridad ${e.prioridad} · ${e.contexto}"
                                    historial = (listOf(resumen) + historial).take(30)
                                    notificacionesActivas = avisosTelefono.publicar(e)
                                    emitir(e.clase)
                                }
                            }
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                next.log.collect { texto ->
                                    if (version == versionEscucha && texto.startsWith("ERROR")) {
                                        detenerEscucha(); mensajeCotidiano = texto
                                    }
                                }
                            }
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                next.nivelMicrofono.collect { nivel ->
                                    if (version != versionEscucha) return@collect
                                    if (nivel != null && nivelMicrofono == null) {
                                        mensajeCotidiano = "Escuchando en $seleccionado."
                                    }
                                    nivelMicrofono = nivel
                                }
                            }
                            mensajeCotidiano = "Abriendo el micrófono…"
                            next.start()
                            awaitCancellation()
                        }
                    } finally {
                        withContext(NonCancellable + Dispatchers.Default) { creado?.releaseAndJoin() }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (version == versionEscucha) mensajeCotidiano = "No se pudo iniciar el motor: ${e.message}" }
            finally {
                if (version == versionEscucha) {
                    escuchando = false
                    nivelMicrofono = null
                }
            }
        }
    }
    fun detenerEscucha() {
        versionEscucha++; escucha?.cancel(); escucha = null
        nivelMicrofono = null
        escuchando = false; detenerAviso(); mensajeCotidiano = "Escucha detenida."
    }

    fun limpiarMusica() {
        versionAnalisis++; analisis?.cancel(); analisis = null
        analizando = false; progresoMusica = 0f; resultadoMusica = null
        detenerVistaPrevia(); mensajeMusica = estadoMusicalInicial()
    }
    fun analizar(uri: Uri) {
        if (analizando) return
        if (!musicaDisponible) { mensajeMusica = estadoMusicalInicial(); return }
        if (!usarValoresPrueba) {
            mensajeMusica = "Faltan parámetros oficiales de hardware. Puedes activar explícitamente los valores de prueba del equipo."
            return
        }
        limpiarMusica(); detenerEscucha()
        val version = versionAnalisis
        val config = HapticsConfig.testValues().copy(exportStems = exportarFuentes)
        analizando = true
        analisis = viewModelScope.launch {
            try {
                val result = exclusividadAnalisis.withLock {
                    RegistroAlgoritmos.crearMusica(getApplication(), config).process(uri) { p ->
                        viewModelScope.launch {
                            if (version == versionAnalisis && analizando) {
                                progresoMusica = p.overall.toFloat().coerceIn(0f, 1f)
                                mensajeMusica = "${etapaMusical(p.stage.name)} · ${(p.overall * 100).toInt()} %"
                            }
                        }
                    }
                }
                if (version == versionAnalisis) {
                    resultadoMusica = result; progresoMusica = 1f
                    mensajeMusica = "Análisis terminado: voz, guitarra y percusión. Parámetros de prueba sin confirmar."
                }
            } catch (e: CancellationException) { throw e }
            catch (e: HapticsException) { if (version == versionAnalisis) mensajeMusica = e.message ?: "No se pudo analizar." }
            catch (e: OutOfMemoryError) { if (version == versionAnalisis) mensajeMusica = "Memoria insuficiente para procesar esta canción." }
            catch (e: Exception) { if (version == versionAnalisis) mensajeMusica = "No se pudo analizar: ${e.message}" }
            finally { if (version == versionAnalisis) analizando = false }
        }
    }
    fun cancelarAnalisis() { limpiarMusica(); mensajeMusica = "Análisis cancelado." }
    fun reproducirResultado(file: File) {
        detenerEscucha(); detenerVistaPrevia()
        try {
            vistaPrevia = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { if (vistaPrevia === it) { it.start(); mensajeVistaPrevia = "Escuchando ${file.name}" } }
                setOnCompletionListener { detenerVistaPrevia() }
                setOnErrorListener { _, _, _ -> detenerVistaPrevia(); mensajeVistaPrevia = "No se pudo escuchar esta señal."; true }
                prepareAsync()
            }
        } catch (e: Exception) { detenerVistaPrevia(); mensajeVistaPrevia = "No se pudo abrir la señal: ${e.message}" }
    }
    fun detenerVistaPrevia() { vistaPrevia?.release(); vistaPrevia = null; mensajeVistaPrevia = "" }
    fun permisoDenegado() { mensajeCotidiano = "Permiso de micrófono denegado. Puedes revisar los contextos y probar los patrones disponibles." }
    fun pausarSalida() { detenerAviso(); detenerVistaPrevia() }
    override fun onCleared() { salida.detener(); detenerVistaPrevia(); bluetooth.desconectar(); super.onCleared() }

    private fun etapaMusical(etapa: String) = when (etapa) {
        "DECODING" -> "Leyendo audio"
        "RESAMPLING" -> "Preparando audio"
        "SEPARATION" -> "Separando instrumentos"
        "TRANSCRIPTION_VOICE" -> "Transcribiendo voz"
        "TRANSCRIPTION_GUITAR" -> "Transcribiendo guitarra"
        "PERCUSSION" -> "Detectando percusión"
        "SYNTHESIS" -> "Generando señales"
        "VALIDATION" -> "Validando señales"
        else -> "Guardando resultados"
    }
}
