package com.tesis.audiotohaptics.audio

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Escritor WAV mono PCM 16 bits little-endian con número de muestras conocido de antemano. */
class Pcm16MonoWavWriter(file: File, val sampleRate: Int, private val totalSamples: Int) : Closeable {
    private val out: BufferedOutputStream
    private var written = 0
    private val bytes = ByteBuffer.allocate(2 * 8192).order(ByteOrder.LITTLE_ENDIAN)

    init {
        file.parentFile?.mkdirs()
        out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
        val dataBytes = totalSamples.toLong() * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt((36 + dataBytes).toInt()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1) // PCM
            putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes.toInt())
        }
        out.write(header.array())
    }

    fun write(samples: ShortArray, count: Int = samples.size) {
        check(written + count <= totalSamples) { "Se escribirían más muestras de las declaradas" }
        var i = 0
        while (i < count) {
            bytes.clear()
            val n = minOf(count - i, bytes.capacity() / 2)
            for (k in 0 until n) bytes.putShort(samples[i + k])
            out.write(bytes.array(), 0, n * 2)
            i += n
        }
        written += count
    }

    override fun close() {
        check(written == totalSamples) { "WAV incompleto: $written de $totalSamples muestras" }
        out.close()
    }
}

/** Formato básico de un WAV leído. */
data class WavInfo(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val formatTag: Int, val frames: Int)

/**
 * Lector de WAV PCM (16/24/32 bits enteros, 32 bits float). Se usa en las pruebas y
 * como alternativa al decodificador de Android para archivos .wav.
 */
object WavReader {
    fun info(file: File): WavInfo = RandomAccessFile(file, "r").use { parse(it).first }

    /** Devuelve (info, canales) con muestras en [-1, 1]. */
    fun read(file: File): Pair<WavInfo, Array<FloatArray>> = RandomAccessFile(file, "r").use { raf ->
        val (info, dataOffset) = parse(raf)
        val bytesPerSample = info.bitsPerSample / 8
        val channels = Array(info.channels) { FloatArray(info.frames) }
        raf.seek(dataOffset)
        val input = DataInputStream(java.io.BufferedInputStream(java.io.FileInputStream(raf.fd), 1 shl 16))
        val frameBytes = ByteArray(bytesPerSample * info.channels)
        val bb = ByteBuffer.wrap(frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until info.frames) {
            input.readFully(frameBytes)
            for (c in 0 until info.channels) {
                val p = c * bytesPerSample
                channels[c][f] = when {
                    info.formatTag == 3 && bytesPerSample == 4 -> bb.getFloat(p)
                    bytesPerSample == 2 -> bb.getShort(p) / 32768f
                    bytesPerSample == 3 -> {
                        val v = (frameBytes[p].toInt() and 0xFF) or ((frameBytes[p + 1].toInt() and 0xFF) shl 8) or
                            (frameBytes[p + 2].toInt() shl 16)
                        v / 8388608f
                    }
                    bytesPerSample == 4 -> bb.getInt(p) / 2147483648f
                    else -> throw IllegalArgumentException("Formato WAV no soportado: ${info.bitsPerSample} bits")
                }
            }
        }
        info to channels
    }

    private fun parse(raf: RandomAccessFile): Pair<WavInfo, Long> {
        val head = ByteArray(12)
        raf.readFully(head)
        require(String(head, 0, 4) == "RIFF" && String(head, 8, 4) == "WAVE") { "No es un archivo WAV RIFF" }
        var fmt: ByteBuffer? = null
        while (raf.filePointer + 8 <= raf.length()) {
            val ch = ByteArray(8)
            raf.readFully(ch)
            val id = String(ch, 0, 4)
            val size = ByteBuffer.wrap(ch, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            if (id == "fmt ") {
                val body = ByteArray(size.toInt())
                raf.readFully(body)
                fmt = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
            } else if (id == "data") {
                val f = requireNotNull(fmt) { "Falta el bloque fmt antes de data" }
                var tag = f.getShort(0).toInt() and 0xFFFF
                val channels = f.getShort(2).toInt()
                val rate = f.getInt(4)
                val bits = f.getShort(14).toInt()
                if (tag == 0xFFFE && f.capacity() >= 26) tag = f.getShort(24).toInt() and 0xFFFF // WAVE_FORMAT_EXTENSIBLE
                require(tag == 1 || tag == 3) { "WAV con códec no PCM (formato $tag)" }
                val available = minOf(size, raf.length() - raf.filePointer)
                val frames = (available / (channels * bits / 8)).toInt()
                return WavInfo(rate, channels, bits, tag, frames) to raf.filePointer
            } else {
                raf.seek(raf.filePointer + size + (size and 1))
            }
        }
        throw IllegalArgumentException("WAV sin bloque data")
    }
}
