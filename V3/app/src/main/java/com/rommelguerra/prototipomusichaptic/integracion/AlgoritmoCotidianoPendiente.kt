package com.rommelguerra.prototipomusichaptic.integracion

/** PUNTO DE INTEGRACIÓN COTIDIANO — EQUIPO DE ALGORITMOS.
 * 1. Pegue/adapte aquí el clasificador Kotlin de sonidos cotidianos.
 * 2. Cambie disponible a true al integrar el algoritmo.
 * 3. Entrada: muestras mono PCM [-1, 1], a 16000 Hz, por bloques de hasta 2048 muestras.
 * 4. Devuelva DeteccionCotidiana(sonido, confianza, patron), o null sin detección.
 * 5. Cargue su modelo una sola vez por instancia y limpie ventanas en reiniciar().
 * 6. Si usa otra clase, regístrela en RegistroAlgoritmos.crearCotidiano().
 * La app ya administra permiso, inicio, parada, avisos visuales y vibración.
 */
class AlgoritmoCotidianoPendiente : AlgoritmoCotidiano {
    override val disponible = false

    override suspend fun detectar(bloque: BloqueAudio): DeteccionCotidiana? {
        // TODO(INTEGRAR_COTIDIANO): sustituir por el algoritmo recibido.
        // Mapear las etiquetas del modelo a SonidoCotidiano y su confianza a [0, 1].
        return null // No presentar demostraciones manuales como detecciones reales.
    }
}
