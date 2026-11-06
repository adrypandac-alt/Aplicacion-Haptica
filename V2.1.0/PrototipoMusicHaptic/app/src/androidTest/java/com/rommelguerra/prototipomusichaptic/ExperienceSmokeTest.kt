package com.rommelguerra.prototipomusichaptic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Recorrido nativo de la entrega sin modelos: no debe simular reconocimiento automático. */
class ExperienceSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navegarYMostrarPerfilesIntegrados() {
        compose.onNodeWithText("Tu mundo,\na tu ritmo.").assertIsDisplayed()
        compose.onNodeWithText("Estado de conexión").assertExists()
        compose.onNodeWithText("Aprendizaje y dispositivo Bluetooth").assertDoesNotExist()
        compose.onNodeWithText("Desafío musical").assertDoesNotExist()
        guardarCaptura("inicio")
        compose.onNodeWithContentDescription("Música", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Seleccionar música").assertExists()
        guardarCaptura("musica")
        compose.onNodeWithText("Analizar música").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Algoritmo de música pendiente de integración.").assertExists()
        compose.onNodeWithContentDescription("Cotidiano", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Iniciar escucha del entorno").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Vibrafil · Social").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Alerta bicicleta").performClick()
        compose.onNodeWithContentDescription("Probar Sirena de emergencia", useUnmergedTree = true).performScrollTo().assertExists()
        guardarCaptura("bicicleta")
        compose.onNodeWithText("Vibrafil · Social").performScrollTo().performClick()
        guardarCaptura("cotidiano")
        compose.onNodeWithContentDescription("Probar Perro", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Señal de demostración activa").assertIsDisplayed()
        compose.onNodeWithText("Detener").performClick()
    }

    private fun guardarCaptura(nombre: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "validacion").apply { mkdirs() }
        File(folder, "$nombre.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Conservar las evidencias aunque Gradle desinstale los paquetes de prueba.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/Download/HapticMusicIntegracion",
            "cp ${File(folder, "$nombre.png").absolutePath} /sdcard/Download/HapticMusicIntegracion/$nombre.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }
}


