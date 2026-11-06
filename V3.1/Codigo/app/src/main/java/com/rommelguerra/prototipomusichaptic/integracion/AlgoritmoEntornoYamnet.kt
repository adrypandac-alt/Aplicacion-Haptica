package com.rommelguerra.prototipomusichaptic.integracion

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Adaptación del YAMNet de AlertaBicicleta al único AudioRecord administrado por la app. */
class AlgoritmoEntornoYamnet(context: Context, private val perfil: PerfilEntorno) : AlgoritmoCotidiano {
    private val app = context.applicationContext
    private var clasificador: Interpreter? = null
    private var etiquetas = emptyList<String>()
    private var entrada: ByteBuffer? = null
    private var salida: ByteBuffer? = null
    private var ventana = FloatArray(0)
    private var usados = 0
    override fun reiniciar() { usados = 0; ventana.fill(0f) }
    override suspend fun detectar(bloque: BloqueAudio): DeteccionCotidiana? {
        require(bloque.frecuenciaMuestreoHz == 16000) { "YAMNet requiere PCM mono a 16000 Hz" }
        currentCoroutineContext().ensureActive()
        val modelo = clasificador ?: cargarModelo()
        var resultado: DeteccionCotidiana? = null
        var offset = 0
        while (offset < bloque.muestras.size) {
            currentCoroutineContext().ensureActive()
            val cantidad = minOf(ventana.size - usados, bloque.muestras.size - offset)
            bloque.muestras.copyInto(ventana, usados, offset, offset + cantidad)
            usados += cantidad; offset += cantidad
            if (usados == ventana.size) {
                val input = checkNotNull(entrada)
                val output = checkNotNull(salida)
                input.rewind(); input.asFloatBuffer().put(ventana)
                output.rewind()
                modelo.run(input, output)
                output.rewind()
                val scores = FloatArray(etiquetas.size)
                output.asFloatBuffer().get(scores)
                val deteccion = CatalogoEntorno.elegir(etiquetas.mapIndexed { i, etiqueta -> etiqueta to scores[i] }, perfil)
                if (deteccion != null && (resultado == null || deteccion.confianza > resultado.confianza)) resultado = deteccion
                // Ventanas completas, solapadas al 50 %, sin relleno artificial en el arranque.
                val salto = ventana.size / 2
                ventana.copyInto(ventana, 0, salto, ventana.size)
                usados = ventana.size - salto
            }
        }
        return resultado
    }
    private fun cargarModelo(): Interpreter {
        etiquetas = app.assets.open("yamnet_label_list.txt").bufferedReader().use { it.readLines() }
        val bytes = app.assets.open("yamnet.tflite").use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes); buffer.rewind()
        // La biblioteca Task Audio 0.4.4 recibida se cerraba en su delegado nativo en Android 17 x86_64.
        // Usar el mismo modelo con Interpreter CPU sin XNNPACK evita ese camino de ejecución.
        val modelo = Interpreter(buffer, Interpreter.Options().setNumThreads(2).setUseXNNPACK(false))
        try {
            val input = modelo.getInputTensor(0)
            val output = modelo.getOutputTensor(0)
            require(input.dataType() == DataType.FLOAT32 && output.dataType() == DataType.FLOAT32)
            require(modelo.outputTensorCount == 1 && output.numElements() == etiquetas.size) { "Modelo y etiquetas incompatibles" }
            ventana = FloatArray(input.numElements())
            require(ventana.size >= 2)
            entrada = ByteBuffer.allocateDirect(input.numBytes()).order(ByteOrder.nativeOrder())
            salida = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
            clasificador = modelo
            return modelo
        } catch (e: Exception) { modelo.close(); throw e }
    }
    override fun cerrar() {
        clasificador?.close(); clasificador = null; ventana = FloatArray(0); usados = 0
        entrada = null; salida = null; etiquetas = emptyList()
    }
}
