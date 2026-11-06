package com.rommelguerra.prototipomusichaptic.integracion

/** Límite de memoria de la aplicación, independiente de la memoria interna del futuro modelo. */
class AcumuladorEventos(private val maximo: Int = 20000) {
    init { require(maximo > 0) }
    private val eventos = mutableListOf<EventoMusical>()
    val cantidad: Int get() = eventos.size

    fun agregar(lote: List<EventoMusical>) {
        // Comprobar ANTES de copiar: un lote excesivo no crece la lista ni deja resultados parciales.
        require(lote.size <= maximo - eventos.size) { "El algoritmo excedió el límite de $maximo eventos" }
        eventos.addAll(lote)
    }

    fun resultado(): List<EventoMusical> = eventos.sortedBy { it.tiempoMs }
}
