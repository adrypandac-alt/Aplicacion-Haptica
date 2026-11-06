package com.rommelguerra.prototipomusichaptic

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.rommelguerra.prototipomusichaptic.integracion.EntradaAudioAndroid
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

class EntradaAudioAndroidTest {
    @Test fun archivoEstereoSeEntregaComoPcmMonoNormalizado() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rate = 44100
        val frames = rate / 4
        val bytes = frames * 4
        val data = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
        data.putInt(16).putShort(1.toShort()).putShort(2.toShort()).putInt(rate).putInt(rate * 4)
        data.putShort(4.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(bytes)
        repeat(frames) { index ->
            val sample = (sin(index * 2 * Math.PI * 440 / rate) * 16384).toInt().toShort()
            data.putShort(sample).putShort(sample)
        }
        val file = File(context.cacheDir, "integracion_pcm_prueba.wav")
        file.writeBytes(data.array())
        try {
            var received = 0
            var peak = 0f
            var previousTime = -1L
            withTimeout(30000) {
                EntradaAudioAndroid(context).leerArchivo(Uri.fromFile(file)) { block ->
                    assertEquals(rate, block.frecuenciaMuestreoHz)
                    assertTrue(block.tiempoInicioMs >= previousTime)
                    previousTime = block.tiempoInicioMs
                    received += block.muestras.size
                    block.muestras.forEach { peak = maxOf(peak, abs(it)) }
                }
            }
            assertEquals(frames, received)
            assertTrue("Amplitud normalizada esperada ~0.5: $peak", peak in 0.49f..0.51f)
        } finally { file.delete() }
    }
}
