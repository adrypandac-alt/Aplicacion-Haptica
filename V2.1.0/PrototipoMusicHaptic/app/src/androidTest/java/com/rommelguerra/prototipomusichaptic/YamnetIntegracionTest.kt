package com.rommelguerra.prototipomusichaptic

import androidx.test.platform.app.InstrumentationRegistry
import com.rommelguerra.prototipomusichaptic.integracion.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class YamnetIntegracionTest {
    @Test fun microfonoProcesaModeloYSeLiberaAlCancelar() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        ).use { it.readBytes() }
        val algoritmo = RegistroAlgoritmos.crearCotidiano(context, PerfilEntorno.VIAL)
        var muestras = 0
        try {
            withTimeoutOrNull(8000) {
                EntradaAudioAndroid(context).escuchar { bloque ->
                    algoritmo.detectar(bloque)
                    muestras += bloque.muestras.size
                }
            }
            assertTrue("Se requiere al menos una ventana real del micrófono", muestras >= 16000)
            // Una segunda captura comprueba que cancelar liberó AudioRecord.
            var nuevaCaptura = false
            withTimeoutOrNull(1000) { EntradaAudioAndroid(context).escuchar { nuevaCaptura = true } }
            assertTrue(nuevaCaptura)
        } finally { algoritmo.cerrar() }
    }

    @Test fun modeloRealCargaProcesaVentanasYCierra() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PerfilEntorno.entries.forEach { perfil ->
            val algoritmo = RegistroAlgoritmos.crearCotidiano(context, perfil)
            try {
                assertTrue(algoritmo.disponible)
                repeat(20) { i ->
                    assertNull(algoritmo.detectar(BloqueAudio(FloatArray(2048), 16000, i * 128L)))
                }
                algoritmo.reiniciar()
                assertNull(algoritmo.detectar(BloqueAudio(FloatArray(2048), 16000, 0)))
            } finally { algoritmo.cerrar() }
        }
    }
}
