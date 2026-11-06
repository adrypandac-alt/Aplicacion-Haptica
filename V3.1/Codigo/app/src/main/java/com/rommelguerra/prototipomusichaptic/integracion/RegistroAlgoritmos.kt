package com.rommelguerra.prototipomusichaptic.integracion

/** Fábricas de las bibliotecas recibidas. No usa los algoritmos pendientes de V3. */
object RegistroAlgoritmos {
    fun crearCotidiano(context: android.content.Context) =
        com.indoamerica.haptica.soundengine.SoundEngine(context)
    fun crearMusica(context: android.content.Context, config: com.tesis.audiotohaptics.config.HapticsConfig) =
        com.tesis.audiotohaptics.AudioToHaptics(context, config)
}
