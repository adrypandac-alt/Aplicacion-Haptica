package com.rommelguerra.prototipomusichaptic.data

import android.provider.BaseColumns

object MusicHapticContract {

    // Tabla de las canciones que vas a reproducir
    object CancionEntry : BaseColumns {
        const val TABLA_CANCIONES = "canciones"
        const val _ID = BaseColumns._ID
        const val COL_TITULO = "titulo"
        const val COL_ARTISTA = "artista"
        const val COL_DURACION = "duracion"
    }

    // Tabla de los tiempos e intensidades de vibración (Efectos hápticos)
    object HapticEntry : BaseColumns {
        const val TABLA_HAPTICOS = "patrones_hapticos"
        const val _ID = BaseColumns._ID
        const val COL_CANCION_ID = "cancion_id" // Une la vibración con una canción específica
        const val COL_TIEMPO_MS = "tiempo_ms"    // Milisegundo exacto en el que vibra
        const val COL_INTENSIDAD = "intensidad"  // Fuerza (0 a 255)
        const val COL_FRECUENCIA = "frecuencia"  // Hz de la vibración
    }
}