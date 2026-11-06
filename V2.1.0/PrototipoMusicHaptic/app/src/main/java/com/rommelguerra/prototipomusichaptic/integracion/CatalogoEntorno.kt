package com.rommelguerra.prototipomusichaptic.integracion

import java.util.Locale

enum class PerfilEntorno(val etiqueta: String) { SOCIAL("Vibrafil · Social"), VIAL("Alerta bicicleta") }

/** Los firmware comparten nombre y UUID, pero aceptan órdenes distintas. */
object CatalogoEntorno {
    const val UMBRAL = 0.35f
    const val SEPARACION_MS = 3000L
    fun sonidos(perfil: PerfilEntorno) = when (perfil) {
        PerfilEntorno.SOCIAL -> listOf(SonidoCotidiano.PERRO, SonidoCotidiano.BEBE, SonidoCotidiano.NOMBRE)
        PerfilEntorno.VIAL -> listOf(SonidoCotidiano.SIRENA, SonidoCotidiano.BOCINA, SonidoCotidiano.ALARMA)
    }
    fun comando(sonido: SonidoCotidiano): String = when (sonido) {
        SonidoCotidiano.PERRO -> "DOG"
        SonidoCotidiano.BEBE -> "BABY"
        SonidoCotidiano.NOMBRE -> "NAME"
        SonidoCotidiano.SIRENA -> "SIREN"
        SonidoCotidiano.BOCINA -> "HORN"
        SonidoCotidiano.ALARMA -> "ALARM"
        else -> error("Sonido sin comando en estos firmware")
    }
    fun patron(sonido: SonidoCotidiano) = PatronHaptico(when (sonido) {
        SonidoCotidiano.PERRO -> listOf(0L, 500, 180, 500, 180, 500)
        SonidoCotidiano.BEBE -> listOf(0L, 300, 150, 300, 150, 300)
        SonidoCotidiano.NOMBRE -> listOf(0L, 180, 120, 180)
        SonidoCotidiano.SIRENA -> listOf(0L, 190, 45, 190, 55, 190, 45, 190, 55, 190, 45, 190)
        SonidoCotidiano.BOCINA -> listOf(0L, 150, 100, 150, 120, 430)
        SonidoCotidiano.ALARMA -> listOf(0L, 110, 45, 110, 70, 110, 45, 110, 70, 110, 45, 110, 70, 110, 45, 110)
        else -> error("Patrón no definido")
    })
    fun clasificar(etiqueta: String, perfil: PerfilEntorno): SonidoCotidiano? {
        val texto = etiqueta.lowercase(Locale.ROOT)
        return when (perfil) {
            PerfilEntorno.SOCIAL -> when (texto) {
                "dog", "bark", "bow-wow", "yip", "howl", "whimper (dog)" -> SonidoCotidiano.PERRO
                "baby cry, infant cry", "baby laughter" -> SonidoCotidiano.BEBE
                else -> null // YAMNet no reconoce identidades ni nombres propios.
            }
            PerfilEntorno.VIAL -> when {
                "siren" in texto -> SonidoCotidiano.SIRENA
                "horn" in texto || "toot" in texto -> SonidoCotidiano.BOCINA
                "alarm" in texto -> SonidoCotidiano.ALARMA
                else -> null
            }
        }
    }
    fun elegir(categorias: List<Pair<String, Float>>, perfil: PerfilEntorno): DeteccionCotidiana? =
        categorias.mapNotNull { (etiqueta, score) ->
            if (!score.isFinite() || score !in UMBRAL..1f) null
            else clasificar(etiqueta, perfil)?.let { DeteccionCotidiana(it, score, patron(it)) }
        }.maxByOrNull { it.confianza }
}
