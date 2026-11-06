package com.rommelguerra.prototipomusichaptic.integracion

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordina los algoritmos con la app. Los equipos implementan los contratos, no esta pantalla. */
class IntegracionViewModel(application: Application) : AndroidViewModel(application) {
    private val entrada = EntradaAudioAndroid(application)
    val bluetooth = ConexionEntornoBle(application)
    private val salida: SalidaHaptica = SalidaHapticaTelefono(application)
    val musicaDisponible = RegistroAlgoritmos.crearMusica().disponible
    val cotidianoDisponible = true
    var perfil by mutableStateOf(PerfilEntorno.SOCIAL)
        private set
    var historial by mutableStateOf(listOf<String>())
        private set
    var mensajeSalida by mutableStateOf("Salida: teléfono")
        private set

    fun cambiarPerfil(nuevo: PerfilEntorno) {
        detenerEscucha()
        perfil = nuevo
        ultimaDeteccion = ""
        mensajeCotidiano = "Pulsa Iniciar escucha para reconocer sonidos."
    }
    fun conectarEsp32() = bluetooth.buscar()
    private var versionSalida = 0
    fun detenerAviso() { versionSalida++; salida.detener(); bluetooth.enviar("STOP") }
    private fun emitir(sonido: SonidoCotidiano) {
        salida.detener()
        val enviado = bluetooth.enviar(CatalogoEntorno.comando(sonido))
        mensajeSalida = if (enviado) "Salida: ESP32 · ${perfil.etiqueta}" else "Salida: teléfono"
        if (!enviado) salida.reproducir(CatalogoEntorno.patron(sonido), intensidad)
    }
    fun probar(sonido: SonidoCotidiano) {
        if (sonido !in CatalogoEntorno.sonidos(perfil)) return
        detenerEscucha()
        val anterior = escucha
        val version = versionSalida
        viewModelScope.launch { anterior?.join(); if (version == versionSalida) emitir(sonido) }
    }
    var mensajeMusica by mutableStateOf(if (musicaDisponible) "Selecciona un audio para analizar." else "Algoritmo de música pendiente de integración.")
        private set
    var mensajeCotidiano by mutableStateOf(if (cotidianoDisponible) "Pulsa Iniciar escucha para reconocer sonidos." else "Algoritmo cotidiano pendiente de integración.")
        private set
    var analizando by mutableStateOf(false)
        private set
    var escuchando by mutableStateOf(false)
        private set
    var ultimaDeteccion by mutableStateOf("")
        private set
    var intensidad = 0.5f
    private var analisis: Job? = null
    private var escucha: Job? = null
    private var secuenciador = SecuenciadorMusical()
    private var versionAnalisis = 0
    // Una cancelación puede tardar en liberar el decodificador/modelo. No ejecutar dos a la vez.
    private val exclusividadAnalisis = Mutex()

    fun limpiarMusica() {
        analisis?.cancel(); analisis = null
        versionAnalisis++
        secuenciador = SecuenciadorMusical(); analizando = false
        salida.detener()
        mensajeMusica = if (musicaDisponible) "Selecciona Analizar música para preparar las señales." else "Algoritmo de música pendiente de integración."
    }

    fun analizar(uri: Uri) {
        if (!musicaDisponible) return
        limpiarMusica()
        val version = versionAnalisis
        analisis = viewModelScope.launch {
            analizando = true
            mensajeMusica = "Analizando archivo…"
            try {
                val algoritmo = RegistroAlgoritmos.crearMusica()
                val resultado = withContext(Dispatchers.Default) {
                    exclusividadAnalisis.withLock {
                        algoritmo.reiniciar()
                        val lista = AcumuladorEventos()
                        entrada.leerArchivo(uri) { bloque -> lista.agregar(algoritmo.procesar(bloque)) }
                        lista.resultado()
                    }
                }
                secuenciador = SecuenciadorMusical(resultado)
                mensajeMusica = "Análisis terminado: ${resultado.size} señales. Reproduce desde el inicio."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mensajeMusica = "No se pudo analizar: ${e.message ?: "error del algoritmo"}" }
            finally { if (version == versionAnalisis) analizando = false }
        }
    }

    /** Llame al buscar una posición, detener o volver a reproducir una canción en bucle. */
    fun reposicionar(tiempoMs: Int) {
        secuenciador.buscar(tiempoMs.toLong())
        salida.detener()
    }

    fun sincronizar(tiempoMs: Int) {
        secuenciador.avanzar(tiempoMs.toLong())?.let {
            mensajeMusica = "Nota: ${it.nota}"
            salida.reproducir(it.patron, intensidad)
        }
    }

    /** Solo se invoca tras obtener permiso de micrófono. Escucha exclusivamente en primer plano. */
    fun iniciarEscucha() {
        if (!cotidianoDisponible || escucha?.isActive == true) return
        val anterior = escucha
        escucha = viewModelScope.launch {
            anterior?.join() // Esperar que una sesión cancelada libere AudioRecord.
            escuchando = true
            ultimaDeteccion = ""
            mensajeCotidiano = "Escuchando sonidos del entorno…"
            val algoritmo = RegistroAlgoritmos.crearCotidiano(getApplication(), perfil)
            try {
                withContext(Dispatchers.Default) {
                    algoritmo.reiniciar()
                    var ultimaEmision = -CatalogoEntorno.SEPARACION_MS
                    entrada.escuchar { bloque ->
                        val deteccion = algoritmo.detectar(bloque)
                        val ahora = SystemClock.elapsedRealtime()
                        // Umbral y separación del algoritmo AlertaBicicleta original.
                        if (deteccion != null && deteccion.confianza >= CatalogoEntorno.UMBRAL && ahora - ultimaEmision >= CatalogoEntorno.SEPARACION_MS) {
                            ultimaEmision = ahora
                            withContext(Dispatchers.Main) {
                                ultimaDeteccion = "${deteccion.sonido.etiqueta} · ${(deteccion.confianza * 100).toInt()} %"
                                emitir(deteccion.sonido)
                                historial = (listOf(ultimaDeteccion) + historial).take(30)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mensajeCotidiano = "No se pudo escuchar: ${e.message ?: "error del algoritmo"}" }
            finally { algoritmo.cerrar(); escuchando = false; salida.detener(); bluetooth.enviar("STOP") }
        }
    }

    fun detenerEscucha() {
        versionSalida++
        escucha?.cancel()
        escuchando = false
        salida.detener()
        bluetooth.enviar("STOP")
        if (cotidianoDisponible) mensajeCotidiano = "Escucha detenida."
    }

    fun permisoDenegado() { mensajeCotidiano = "Permiso de micrófono denegado. Puedes usar las demostraciones manuales." }
    fun pausarSalida() = detenerAviso()
    override fun onCleared() { salida.detener(); bluetooth.desconectar(); super.onCleared() }
}

