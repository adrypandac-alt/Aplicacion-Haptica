package com.indoamerica.haptica.soundengine

import kotlinx.coroutines.flow.Flow

interface SoundRecognizer {
    fun setContext(contexto: String)
    fun start()
    fun stop()
    fun release()
    val events: Flow<SoundEvent>   // alertas confirmadas
    val log: Flow<String>          // todas las detecciones (depuración)
    val contextosDisponibles: List<String>
}