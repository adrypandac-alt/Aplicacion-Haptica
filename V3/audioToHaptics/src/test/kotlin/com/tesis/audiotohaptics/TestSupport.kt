package com.tesis.audiotohaptics

import com.tesis.audiotohaptics.config.HapticsConfig
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.log10

/** Rutas a recursos de validación externos al módulo (modelos, prototipo Python). */
object TestPaths {
    /** Carpeta Tesis-1 (contiene Practica/ y AudioToHaptics/). */
    val repoRoot = File(System.getProperty("ath.repoRoot") ?: "../..")
    val projectRoot = File(System.getProperty("ath.projectRoot") ?: "..")
    val practica = File(repoRoot, "Practica")
    val models = File(projectRoot, "models")
    val fixtures = File(models, "fixtures")
    val demucsModel = File(models, "htdemucs_6s.onnx")
    val basicPitchModel = File(projectRoot, "audioToHaptics/src/main/assets/audiotohaptics/models/basic_pitch_nmp.onnx")
    val results = File(projectRoot, "docs/resultados")

    /** Omite la prueba (no la da por superada) si falta un recurso externo. */
    fun require(vararg files: File) {
        for (f in files) assumeTrue("Recurso no disponible: $f", f.exists())
    }

    fun tempDir(name: String): File =
        File(projectRoot, "test-output/$name").apply { deleteRecursively(); mkdirs() }
}

fun readF32(file: File): FloatArray {
    val bytes = file.readBytes()
    val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(fb.remaining()).also { fb.get(it) }
}

fun sdrDb(reference: FloatArray, estimate: FloatArray): Double {
    var num = 0.0
    var den = 0.0
    for (i in reference.indices) {
        num += reference[i].toDouble() * reference[i]
        val d = reference[i].toDouble() - estimate[i]
        den += d * d
    }
    return 10 * log10(num / maxOf(den, 1e-20))
}

/** Configuración de PRUEBA (no confirmada) con fs pequeña para pruebas rápidas. */
fun testConfig(sampleRate: Int = 8000): HapticsConfig = HapticsConfig.testValues().copy(sampleRateHz = sampleRate)

/** Golpes sintéticos: ruido con decaimiento exponencial (tau 15 ms) + ruido de fondo -60 dB. */
fun percussiveAudio(hitTimes: List<Double>, durationS: Double, sampleRate: Int = 44100, seed: Long = 0): FloatArray {
    val rng = java.util.Random(seed)
    val audio = FloatArray((durationS * sampleRate).toInt())
    val length = (0.15 * sampleRate).toInt()
    for (t in hitTimes) {
        val start = (t * sampleRate).toInt()
        for (k in 0 until length) {
            val i = start + k
            if (i >= audio.size) break
            audio[i] += (rng.nextGaussian() * exp(-k / (0.015 * sampleRate)) * 0.8).toFloat()
        }
    }
    for (i in audio.indices) audio[i] += (rng.nextGaussian() * 1e-3).toFloat()
    return audio
}
