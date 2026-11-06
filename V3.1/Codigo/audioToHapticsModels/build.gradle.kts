import java.security.MessageDigest

plugins {
    id("com.android.library")
}

/*
 * Paquete de modelos para funcionamiento sin conexión.
 *
 * Contiene htdemucs_6s.onnx (≈115 MB) como asset. Se empaqueta en el APK de la
 * app que lo incluya, de modo que tras instalar no hace falta ninguna descarga.
 * El archivo NO se versiona en Git: se genera con tools/model_export/export_demucs.py
 * y se coloca en AudioToHaptics/models/. La compilación falla si falta o si su
 * huella SHA-256 no coincide con la del modelo validado (docs/MODELOS.md).
 */

val demucsFile = "htdemucs_6s.onnx"
val demucsSha256 = "16ebcff7c392fc1a21bd2574ea686d20c0ffd9abfd7dcb56680359cb5450c0e0"
val generatedAssets = layout.buildDirectory.dir("generated/modelAssets")

android {
    namespace = "com.tesis.audiotohaptics.models"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    sourceSets["main"].assets.srcDir(generatedAssets)
    androidResources { noCompress += "onnx" }
}

val prepareModelAssets by tasks.registering {
    val source = rootProject.layout.projectDirectory.file("models/$demucsFile").asFile
    val target = generatedAssets.map { it.file("audiotohaptics/models/$demucsFile").asFile }
    inputs.file(source).optional()
    outputs.dir(generatedAssets)
    doLast {
        if (!source.isFile) {
            throw GradleException(
                "Falta ${source.path}. Genérelo con tools/model_export/export_demucs.py " +
                    "(ver docs/MODELOS.md). No se descarga en tiempo de ejecución."
            )
        }
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != demucsSha256) {
            throw GradleException("SHA-256 de $demucsFile no coincide: $actual (esperado $demucsSha256)")
        }
        val out = target.get()
        out.parentFile.mkdirs()
        source.copyTo(out, overwrite = true)
    }
}

tasks.named("preBuild") { dependsOn(prepareModelAssets) }
