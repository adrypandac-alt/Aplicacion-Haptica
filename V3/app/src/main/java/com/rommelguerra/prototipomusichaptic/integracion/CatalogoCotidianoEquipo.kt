package com.rommelguerra.prototipomusichaptic.integracion

import android.content.Context
import org.json.JSONObject

data class CasoCotidiano(val clase: String, val prioridad: Int, val umbral: Double, val entrenado: Boolean)

/** Lee los mismos assets del motor: no inventa ni modifica sus reglas. */
class CatalogoCotidianoEquipo(context: Context) {
    val clases = context.assets.open("clases.txt").bufferedReader().use { it.readLines().map(String::trim).filter(String::isNotEmpty).toSet() }
    private val json = context.assets.open("contexts.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("contexts") }
    val contextos = json.keys().asSequence().toList().sorted()
    fun casos(contexto: String): List<CasoCotidiano> {
        val reglas = json.getJSONObject(contexto)
        return reglas.keys().asSequence().map { clase ->
            val r = reglas.getJSONObject(clase)
            CasoCotidiano(clase, r.getInt("prioridad"), r.getDouble("umbral"), clase in clases)
        }.sortedWith(compareByDescending<CasoCotidiano> { it.prioridad }.thenBy { it.clase }).toList()
    }
    companion object {
        fun etiqueta(clase: String) = when (clase) {
            "claxon" -> "Claxon"
            "ladrido" -> "Ladrido"
            "llanto_bebe" -> "Llanto de bebé"
            "maquinaria" -> "Maquinaria"
            "sirena" -> "Sirena"
            "vidrio_roto" -> "Vidrio roto"
            "alarma_humo" -> "Alarma de humo"
            "timbre" -> "Timbre"
            "nombre_usuario" -> "Nombre del usuario"
            "fondo" -> "Fondo"
            else -> clase
        }
        fun sonido(clase: String): SonidoCotidiano? = when (clase) {
            "ladrido" -> SonidoCotidiano.PERRO
            "llanto_bebe" -> SonidoCotidiano.BEBE
            "sirena" -> SonidoCotidiano.SIRENA
            "claxon" -> SonidoCotidiano.BOCINA
            else -> null
        }
    }
}
