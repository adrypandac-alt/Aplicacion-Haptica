package com.rommelguerra.prototipomusichaptic.integracion

/** Línea de tiempo sin dependencias Android, para sincronizar análisis y reproducción. */
class SecuenciadorMusical(eventos: List<EventoMusical> = emptyList()) {
    private val eventos = eventos.sortedBy { it.tiempoMs }
    private var siguiente = 0

    fun buscar(tiempoMs: Long) {
        require(tiempoMs >= 0)
        siguiente = limite(tiempoMs, incluirIguales = false)
    }

    /** Si varias notas coinciden, gana la más reciente. No repite señales ya consumidas. */
    fun avanzar(tiempoMs: Long): EventoMusical? {
        val hasta = limite(tiempoMs, incluirIguales = true)
        if (hasta <= siguiente) return null
        siguiente = hasta
        return eventos[hasta - 1]
    }

    // Búsqueda binaria: evita recorrer hasta 20000 eventos al arrastrar la barra de tiempo.
    private fun limite(tiempoMs: Long, incluirIguales: Boolean): Int {
        var inicio = 0
        var fin = eventos.size
        while (inicio < fin) {
            val medio = (inicio + fin) ushr 1
            val tiempo = eventos[medio].tiempoMs
            if (tiempo < tiempoMs || (incluirIguales && tiempo == tiempoMs)) inicio = medio + 1 else fin = medio
        }
        return inicio
    }
}
