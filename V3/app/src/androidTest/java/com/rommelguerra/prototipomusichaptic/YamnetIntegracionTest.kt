package com.rommelguerra.prototipomusichaptic

import androidx.test.platform.app.InstrumentationRegistry
import com.rommelguerra.prototipomusichaptic.integracion.*
import com.indoamerica.haptica.soundengine.SoundEngine
import com.tesis.audiotohaptics.config.HapticsConfig
import com.tesis.audiotohaptics.config.HapticsException
import android.net.Uri
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YamnetIntegracionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun contextosYCategoriasCoincidenConLosAssets() {
        val catalogo = CatalogoCotidianoEquipo(context)
        assertEquals(listOf("hogar", "industrial", "social", "urbano"), catalogo.contextos)
        assertEquals(7, catalogo.clases.size)
        assertEquals(setOf("ladrido", "llanto_bebe", "vidrio_roto"), catalogo.casos("hogar").filter { it.entrenado }.map { it.clase }.toSet())
        assertEquals(listOf("sirena"), catalogo.casos("social").filter { it.entrenado }.map { it.clase })
        assertEquals(setOf("maquinaria", "sirena"), catalogo.casos("industrial").filter { it.entrenado }.map { it.clase }.toSet())
        assertEquals(setOf("sirena", "claxon"), catalogo.casos("urbano").filter { it.entrenado }.map { it.clase }.toSet())
        assertFalse(catalogo.casos("hogar").first { it.clase == "timbre" }.entrenado)
    }

    @Test fun modelosRealesEjecutanYamnetYClasificadorDeSieteClases() {
        fun load(name: String): Interpreter {
            val bytes = context.assets.open(name).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes); buffer.rewind()
            return Interpreter(buffer, Interpreter.Options().setNumThreads(2))
        }
        load("yamnet.tflite").use { yamnet ->
            yamnet.resizeInput(0, intArrayOf(SoundEngine.WINDOW)); yamnet.allocateTensors()
            val idx = (0 until yamnet.outputTensorCount).first { yamnet.getOutputTensor(it).shape().contentEquals(intArrayOf(1, 1024)) }
            val emb = Array(1) { FloatArray(1024) }
            yamnet.runForMultipleInputsOutputs(arrayOf<Any>(FloatArray(SoundEngine.WINDOW)), hashMapOf<Int, Any>(idx to emb))
            assertTrue(emb[0].all { it.isFinite() })
            load("head.tflite").use { head ->
                val probs = Array(1) { FloatArray(7) }
                head.run(emb, probs)
                assertTrue(probs[0].all { it.isFinite() && it in 0f..1f })
                assertEquals(1.0, probs[0].sum().toDouble(), 0.001)
            }
        }
    }

    @Test fun inicioYCierreSeguroLiberanMicrofonoYModelos() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        ).use { it.readBytes() }
        repeat(2) {
            val engine = RegistroAlgoritmos.crearCotidiano(context)
            val inicio = CompletableDeferred<String>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                engine.log.collect { line -> if (!inicio.isCompleted) inicio.complete(line) }
            }
            try {
                engine.setContext("hogar")
                engine.start()
                assertTrue(withTimeout(10000) { inicio.await() }.startsWith("Escuchando"))
                delay(1800)
            } finally { engine.releaseAndJoin(); collector.cancelAndJoin() }
        }
    }

    @Test fun musicaReconoceModeloAusenteSinIntentarDescargarlo() = runBlocking {
        val engine = RegistroAlgoritmos.crearMusica(context, HapticsConfig.testValues())
        assertTrue(engine.modelStatus().basicPitchInstalled)
        assertFalse(engine.modelStatus().demucsInstalled)
        try {
            engine.process(Uri.EMPTY)
            fail("Debe informar el modelo pendiente")
        } catch (e: HapticsException.ModelNotAvailable) {
            assertEquals("htdemucs_6s.onnx", e.model)
        }
    }
}
