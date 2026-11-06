package com.indoamerica.haptica.soundengine

import org.json.JSONObject
import kotlin.collections.iterator

class ClassRule(val prioridad: Int, val umbral: Float, val nivelMinDb: Float)

class ContextConfig(json: String) {
    private val contextos = HashMap<String, Map<String, ClassRule>>()

    init {
        val raiz = JSONObject(json).getJSONObject("contexts")
        for (ctx in raiz.keys()) {
            val reglas = HashMap<String, ClassRule>()
            val obj = raiz.getJSONObject(ctx)
            for (clase in obj.keys()) {
                val r = obj.getJSONObject(clase)
                reglas[clase] = ClassRule(
                    r.getInt("prioridad"),
                    r.getDouble("umbral").toFloat(),
                    r.optDouble("nivel_min_db", 0.0).toFloat()
                )
            }
            contextos[ctx] = reglas
        }
    }

    fun rule(contexto: String, clase: String): ClassRule? = contextos[contexto]?.get(clase)
    val nombres: List<String> get() = contextos.keys.sorted()
}