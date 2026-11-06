package com.rommelguerra.prototipomusichaptic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test

class ExperienceSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demostracionesVisualesNoSeRegistranComoDetecciones() {
        compose.onNodeWithContentDescription("Cotidiano", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("probar_ladrido").performScrollTo().performClick()
        compose.onNodeWithTag("aviso_ladrido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Demostración manual").assertIsDisplayed()
        compose.onNodeWithTag("probar_vidrio_roto").performScrollTo().performClick()
        compose.onNodeWithTag("aviso_vidrio_roto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aviso_ladrido").assertDoesNotExist()
        compose.onNodeWithText("Detecciones de esta sesión").assertDoesNotExist()
        compose.onNodeWithText("Última detección").assertDoesNotExist()

        compose.onNodeWithTag("contexto_urbano").performScrollTo().performClick()
        compose.onNodeWithTag("probar_sirena").performScrollTo().performClick()
        compose.onNodeWithTag("aviso_sirena").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sirena de emergencia").assertIsDisplayed()
    }

    @Test fun interfazMuestraContextosYLimitesRealesDeLosEquipos() {
        compose.onNodeWithText("Tu mundo,\na tu ritmo.").assertIsDisplayed()
        compose.onNodeWithText("Estado de conexión").assertExists()
        compose.onNodeWithText("Desafío musical").assertDoesNotExist()
        guardarCaptura("inicio")
        compose.onNodeWithContentDescription("Música", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Basic Pitch: incluido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Demucs: no incluido").assertExists()
        compose.onNodeWithText("Analizar música").performScrollTo().assertIsNotEnabled()
        guardarCaptura("musica_modelos")
        compose.onNodeWithContentDescription("Cotidiano", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("contexto_hogar").assertIsSelected()
        compose.onNodeWithTag("caso_llanto_bebe").performScrollTo().assertExists()
        compose.onNodeWithTag("caso_vidrio_roto").performScrollTo().assertExists()
        guardarCaptura("cotidiano_hogar")
        compose.onNodeWithTag("contexto_social").performScrollTo().performClick()
        compose.onNodeWithText("Sonidos en social").assertExists()
        compose.onNodeWithTag("caso_llanto_bebe").assertDoesNotExist()
        compose.onNodeWithTag("caso_sirena").assertExists()
        compose.onNodeWithTag("contexto_industrial").performScrollTo().performClick()
        compose.onNodeWithTag("caso_maquinaria").performScrollTo().assertExists()
        guardarCaptura("cotidiano_industrial")
        compose.onNodeWithTag("contexto_urbano").performScrollTo().performClick()
        compose.onNodeWithTag("caso_claxon").assertExists()
        compose.onNodeWithTag("caso_maquinaria").assertDoesNotExist()
    }

    private fun guardarCaptura(nombre: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.getExternalFilesDir(null), "validacion_integracion").apply { mkdirs() }
        val file = File(folder, "$nombre.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        listOf("mkdir -p /sdcard/Download/V3Integracion1", "cp ${file.absolutePath} /sdcard/Download/V3Integracion1/$nombre.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }
}
