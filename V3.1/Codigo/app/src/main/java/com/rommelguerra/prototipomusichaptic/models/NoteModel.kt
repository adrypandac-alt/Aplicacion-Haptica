package com.rommelguerra.prototipomusichaptic.models

/**
 * Representa una nota musical y su equivalente háptico.
 *
 * La nota se codifica mediante:
 *
 * 1. Frecuencia vibrotáctil.
 * 2. Amplitud.
 * 3. Ubicación espacial entre dos actuadores.
 */
data class NoteModel(

    // Nombre de la nota.
    // Ejemplo: DO, DO#, RE...
    val nombre: String,

    // Texto mostrado en la interfaz.
    val descripcionVisual: String,

    // Patrón temporal de vibración.
    // Ejemplo: [0, 300]
    val patronVibracion: LongArray,

    // Amplitudes utilizadas por el vibrador del teléfono.
    // Escala Android: 0 - 255.
    val amplitudes: IntArray,

    // Frecuencia vibrotáctil exacta.
    val frecuenciaHz: Double,

    // Posición espacial:
    //
    // 0.0  = actuador izquierdo
    // 5.0  = centro
    // 10.0 = actuador derecho
    val ubicacion: Float,

    // Intensidad lógica utilizada por el brazalete.
    // Se expresa como porcentaje.
    //
    // 0   = apagado
    // 100 = intensidad máxima configurada
    val amplitudBrazalete: Int = 100,

    // Duración de reproducción de la nota.
    val duracionMs: Int = 300

) {

    override fun equals(other: Any?): Boolean {

        if (this === other) return true

        if (javaClass != other?.javaClass) {
            return false
        }

        other as NoteModel

        if (nombre != other.nombre) return false

        if (!patronVibracion.contentEquals(
                other.patronVibracion
            )
        ) return false

        if (!amplitudes.contentEquals(
                other.amplitudes
            )
        ) return false

        if (frecuenciaHz != other.frecuenciaHz) {
            return false
        }

        if (ubicacion != other.ubicacion) {
            return false
        }

        if (amplitudBrazalete !=
            other.amplitudBrazalete
        ) {
            return false
        }

        if (duracionMs != other.duracionMs) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {

        var result = nombre.hashCode()

        result =
            31 * result +
                    patronVibracion.contentHashCode()

        result =
            31 * result +
                    amplitudes.contentHashCode()

        result =
            31 * result +
                    frecuenciaHz.hashCode()

        result =
            31 * result +
                    ubicacion.hashCode()

        result =
            31 * result +
                    amplitudBrazalete

        result =
            31 * result +
                    duracionMs

        return result
    }
}