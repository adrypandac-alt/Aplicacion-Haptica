package com.rommelguerra.prototipomusichaptic.integracion

/** PUNTO DE INTEGRACIÓN MÚSICA — EQUIPO DE ALGORITMOS.
 * 1. Coloque aquí el algoritmo Kotlin que le entreguen, o cree una clase AlgoritmoMusica.
 * 2. Cambie disponible a true SOLO cuando la implementación sea real.
 * 3. Acumule ventanas si necesita más muestras, identifique notas/ritmos y devuelva
 *    EventoMusical(tiempoMs, nota, PatronHaptico(...)). No vibre durante el análisis:
 *    la aplicación sincroniza estas señales con la reproducción del archivo.
 * 4. Use reiniciar() para limpiar el estado entre canciones/análisis.
 * 5. Si usa otra clase, regístrela en RegistroAlgoritmos.crearMusica().
 */
class AlgoritmoMusicaPendiente : AlgoritmoMusica {
    override val disponible = false

    override suspend fun procesar(bloque: BloqueAudio): List<EventoMusical> {
        // TODO(INTEGRAR_MUSICA): sustituir por el algoritmo recibido.
        // Entrada: bloque.muestras, bloque.frecuenciaMuestreoHz, bloque.tiempoInicioMs.
        // Salida: lista de notas/patrones con tiempos absolutos en milisegundos.
        return emptyList() // No inventar resultados mientras el algoritmo está pendiente.
    }
}
