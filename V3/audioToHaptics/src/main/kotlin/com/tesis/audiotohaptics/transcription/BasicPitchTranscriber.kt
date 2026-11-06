package com.tesis.audiotohaptics.transcription

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.floor
import kotlin.math.roundToInt

/** Nota transcrita en segundos desde el inicio del audio (como el CSV de Basic Pitch). */
data class NoteEvent(val startS: Double, val endS: Double, val pitch: Int, val velocity: Int)

/**
 * Transcripción polifónica con el modelo ONNX oficial de Basic Pitch (Spotify, Apache-2.0),
 * incluido en los assets de la biblioteca (228 KB).
 *
 * Reproduce basic_pitch.inference.predict: audio mono a 22050 Hz, ventanas de 43844
 * muestras con 30 frames de solapamiento, posprocesamiento NoteCreation y la corrección
 * temporal de model_frames_to_time. No se calculan pitch bends.
 */
class BasicPitchTranscriber(modelFile: File, threads: Int = 0) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions().apply { if (threads > 0) setIntraOpNumThreads(threads) },
    )

    /** @param audio mono a [SAMPLE_RATE] Hz. */
    fun transcribe(audio: FloatArray, onWindow: (Int, Int) -> Unit = { _, _ -> }): List<NoteEvent> {
        val (frames, onsets, nFrames) = infer(audio, onWindow)
        val minLen = (127.70 / 1000 * (SAMPLE_RATE.toDouble() / FFT_HOP)).roundToInt()
        val times = framesToTime(nFrames)
        return NoteCreation.notesPolyphonic(frames, onsets, nFrames, minNoteLength = minLen).map {
            NoteEvent(times[it.startFrame], times[it.endFrame], it.pitch, (127 * it.amplitude).roundToInt())
        }
    }

    internal data class Activations(val frames: FloatArray, val onsets: FloatArray, val nFrames: Int)

    internal fun infer(audio: FloatArray, onWindow: (Int, Int) -> Unit): Activations {
        val padded = FloatArray(OVERLAP_LEN / 2 + audio.size)
        System.arraycopy(audio, 0, padded, OVERLAP_LEN / 2, audio.size)
        val starts = (0 until padded.size step HOP_SIZE).toList()
        val half = N_OVERLAP_FRAMES / 2
        val keep = ANNOT_N_FRAMES - 2 * half
        val nFrames = floor(audio.size * ((SAMPLE_RATE / FFT_HOP).toDouble() / SAMPLE_RATE)).toInt()
        val frames = FloatArray(nFrames * P)
        val onsets = FloatArray(nFrames * P)
        val window = FloatArray(AUDIO_N_SAMPLES)
        var written = 0
        for ((w, start) in starts.withIndex()) {
            onWindow(w, starts.size)
            window.fill(0f)
            System.arraycopy(padded, start, window, 0, minOf(AUDIO_N_SAMPLES, padded.size - start))
            OnnxTensor.createTensor(env, FloatBuffer.wrap(window), longArrayOf(1, AUDIO_N_SAMPLES.toLong(), 1)).use { x ->
                session.run(mapOf(INPUT to x), setOf(OUT_NOTE, OUT_ONSET)).use { r ->
                    val note = (r.get(OUT_NOTE).get() as OnnxTensor).floatBuffer
                    val onset = (r.get(OUT_ONSET).get() as OnnxTensor).floatBuffer
                    // unwrap_output: se descartan 15 frames al principio y al final de cada ventana.
                    for (t in 0 until keep) {
                        if (written >= nFrames) break
                        for (f in 0 until P) {
                            frames[written * P + f] = note.get((t + half) * P + f)
                            onsets[written * P + f] = onset.get((t + half) * P + f)
                        }
                        written++
                    }
                }
            }
        }
        onWindow(starts.size, starts.size)
        return Activations(frames, onsets, nFrames)
    }

    override fun close() = session.close()

    companion object {
        const val SAMPLE_RATE = 22050
        const val FFT_HOP = 256
        const val AUDIO_N_SAMPLES = SAMPLE_RATE * 2 - FFT_HOP // 43844
        const val ANNOT_N_FRAMES = (SAMPLE_RATE / FFT_HOP) * 2 // 172
        const val N_OVERLAP_FRAMES = 30
        const val OVERLAP_LEN = N_OVERLAP_FRAMES * FFT_HOP
        const val HOP_SIZE = AUDIO_N_SAMPLES - OVERLAP_LEN
        private const val P = NoteCreation.N_PITCHES
        private const val INPUT = "serving_default_input_2:0"
        private const val OUT_NOTE = "StatefulPartitionedCall:1"
        private const val OUT_ONSET = "StatefulPartitionedCall:2"

        /** basic_pitch.note_creation.model_frames_to_time (incluye su corrección de 0,0018 s). */
        fun framesToTime(nFrames: Int): DoubleArray {
            val windowOffset = (FFT_HOP.toDouble() / SAMPLE_RATE) *
                (ANNOT_N_FRAMES - AUDIO_N_SAMPLES.toDouble() / FFT_HOP) + 0.0018
            return DoubleArray(nFrames) { i ->
                i * FFT_HOP.toDouble() / SAMPLE_RATE - windowOffset * floor(i.toDouble() / ANNOT_N_FRAMES)
            }
        }
    }
}
