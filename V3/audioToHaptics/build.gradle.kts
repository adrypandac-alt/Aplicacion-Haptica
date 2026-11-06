plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val ortVersion = "1.20.0"

android {
    namespace = "com.tesis.audiotohaptics"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    // Los modelos .onnx se guardan sin comprimir en el APK para poder copiarlos rápido.
    androidResources { noCompress += "onnx" }

    testOptions {
        unitTests.all {
            it.maxHeapSize = "3g"
            // Rutas a recursos de validación fuera del módulo (modelos y audio del prototipo).
            it.systemProperty("ath.repoRoot", rootProject.projectDir.parentFile.absolutePath)
            it.systemProperty("ath.projectRoot", rootProject.projectDir.absolutePath)
            it.systemProperty("ath.longTests", providers.gradleProperty("athLongTests").getOrElse("false"))
            it.testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Motor de inferencia local (sin red). Licencia MIT.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:$ortVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // En las pruebas JVM se usa la misma API de ONNX Runtime con binarios de escritorio.
    testImplementation("com.microsoft.onnxruntime:onnxruntime:$ortVersion")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

// onnxruntime-android trae binarios .so de Android que no cargan en el JVM de escritorio:
// en las pruebas unitarias se sustituye por el artefacto de escritorio (misma API Java).
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}


