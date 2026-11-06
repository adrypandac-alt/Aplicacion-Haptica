package com.indoamerica.haptica.soundengine

data class SoundEvent(
    val clase: String,
    val confianza: Float,
    val prioridad: Int,
    val contexto: String,
    val nivelDb: Float,
    val timestamp: Long
)