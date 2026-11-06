package com.rommelguerra.prototipomusichaptic.integracion

/** Entrada común para los dos equipos. PCM mono normalizado a [-1, 1].
 * frecuenciaMuestreoHz es la frecuencia REAL de la fuente; no asumir 16000 Hz para archivos.
 * tiempoInicioMs es relativo al inicio del archivo o de la sesión de micrófono.
 * Cada array pertenece a este bloque y puede conservarse si el algoritmo necesita ventanas.
 */
data class BloqueAudio(
    val muestras: FloatArray,
    val frecuenciaMuestreoHz: Int,
    val tiempoInicioMs: Long
)

/** Alterna silencio/vibración, comenzando con silencio. Máximo cinco segundos por señal.
 * Ejemplo: listOf(0L, 180L, 180L, 180L) = dos pulsos.
 * amplitud va de 1 a 255; intensidad del usuario se aplica en la salida.
 */
data class PatronHaptico(val tiemposMs: List<Long>, val amplitud: Int = 160) {
    init {
        require(tiemposMs.size >= 2 && tiemposMs.size <= 32)
        require(tiemposMs.all { it in 0..5000 } && tiemposMs.sum() in 1..5000)
        require(tiemposMs.filterIndexed { i, _ -> i % 2 == 1 }.any { it > 0 })
        require(amplitud in 1..255)
    }
}

data class EventoMusical(val tiempoMs: Long, val nota: String, val patron: PatronHaptico) {
    init { require(tiempoMs >= 0 && nota.isNotBlank()) }
}

enum class SonidoCotidiano(val etiqueta: String) {
    TIMBRE("Timbre"), LLAMADA("Llamada"), RECORDATORIO("Recordatorio"), ALERTA("Alerta"),
    PERRO("Perro"), BEBE("Bebé"), NOMBRE("Nombre propio"),
    SIRENA("Sirena de emergencia"), BOCINA("Bocina de vehículo"), ALARMA("Alarma urbana")
}

data class DeteccionCotidiana(
    val sonido: SonidoCotidiano,
    val confianza: Float,
    val patron: PatronHaptico
) {
    init { require(confianza.isFinite() && confianza in 0f..1f) }
}

/** Se ejecuta fuera del hilo de interfaz. El archivo se entrega en bloques sucesivos.
 * Si el modelo requiere otra tasa, el EQUIPO DEL ALGORITMO debe remuestrear las muestras.
 * Devuelva tiempos absolutos del archivo, no tiempos relativos a cada bloque.
 */
interface AlgoritmoMusica {
    val disponible: Boolean get() = true
    fun reiniciar() {}
    suspend fun procesar(bloque: BloqueAudio): List<EventoMusical>
}

/** Audio de micrófono mono a 16000 Hz. Devuelva null cuando no haya una detección.
 * Debe responder por bloque sin abrir micrófonos ni modificar pantallas.
 */
interface AlgoritmoCotidiano {
    val disponible: Boolean get() = true
    fun reiniciar() {}
    fun cerrar() {}
    suspend fun detectar(bloque: BloqueAudio): DeteccionCotidiana?
}
