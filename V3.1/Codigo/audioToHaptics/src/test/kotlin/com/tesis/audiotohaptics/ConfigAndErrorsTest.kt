package com.tesis.audiotohaptics

import com.tesis.audiotohaptics.config.Actuator
import com.tesis.audiotohaptics.config.ActuatorConfig
import com.tesis.audiotohaptics.config.HapticsConfig
import com.tesis.audiotohaptics.config.HapticsException
import com.tesis.audiotohaptics.pipeline.DecodedAudio
import com.tesis.audiotohaptics.pipeline.HapticsPipeline
import com.tesis.audiotohaptics.pipeline.ModelFiles
import com.tesis.audiotohaptics.storage.ArrayFloatSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ConfigAndErrorsTest {
    @Test
    fun officialConfigReportsPendingHardwareData() {
        try {
            HapticsConfig.default().validate()
            fail("Debía rechazar la configuración pendiente")
        } catch (e: HapticsException.ConfigurationPending) {
            assertEquals(
                listOf("sampleRateHz", "actuators.VOICE.carrierHz", "actuators.GUITAR.carrierHz", "actuators.PERCUSSION.carrierHz"),
                e.missing,
            )
        }
    }

    @Test
    fun testValuesAreValidButNotConfirmed() {
        val c = HapticsConfig.testValues()
        c.validate()
        assertFalse(c.confirmed)
    }

    @Test(expected = HapticsException.InvalidConfiguration::class)
    fun carrierAboveNyquistIsRejected() {
        val c = testConfig(8000)
        c.copy(actuators = c.actuators + (Actuator.VOICE to ActuatorConfig(1, 5000.0, "v.wav"))).validate()
    }

    @Test
    fun jsonRoundTrip() {
        val c = HapticsConfig.testValues()
        assertEquals(c, HapticsConfig.fromJson(c.toJson()))
        assertTrue(HapticsConfig.default().toJson().contains("\"carrierHz\": null"))
    }

    @Test
    fun missingModelIsReported() {
        val audio = DecodedAudio(listOf(ArrayFloatSignal(FloatArray(44100))), 44100, "silencio")
        val pipeline = HapticsPipeline(testConfig(), ModelFiles(File("no_existe.onnx"), File("no_existe2.onnx")),
            TestPaths.tempDir("err_work"))
        try {
            pipeline.run(audio, TestPaths.tempDir("err_out"))
            fail("Debía fallar")
        } catch (e: HapticsException.ModelNotAvailable) {
            assertEquals("htdemucs_6s", e.model)
        }
    }

    @Test(expected = HapticsException.ConfigurationPending::class)
    fun pipelineRefusesPendingConfig() {
        val audio = DecodedAudio(listOf(ArrayFloatSignal(FloatArray(10))), 44100, "x")
        HapticsPipeline(HapticsConfig.default(), ModelFiles(File("a"), File("b")), TestPaths.tempDir("err2")).run(audio, TestPaths.tempDir("err2o"))
    }

    /**
     * Verificación estática de ausencia de red: el código de la biblioteca no usa ninguna
     * API de red. (El manifiesto fusionado de la app se comprueba en la compilación de demoApp.)
     */
    @Test
    fun libraryCodeHasNoNetworkApis() {
        val src = File(TestPaths.projectRoot, "audioToHaptics/src/main")
        val forbidden = Regex("""java\.net\.|HttpURLConnection|okhttp|retrofit|android\.net\.http|Socket\(|URL\(|android\.permission\.INTERNET""")
        val hits = src.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (forbidden.containsMatchIn(l)) "${f.name}:${i + 1}: $l" else null } }
            .toList()
        assertTrue("Uso de red encontrado:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
