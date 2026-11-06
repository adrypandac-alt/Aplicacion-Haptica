package com.tesis.audiotohaptics.storage

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

/** Señal de solo lectura indexada por muestra. */
interface FloatSignal {
    val size: Int
    operator fun get(index: Int): Float
}

/** Señal modificable. */
interface MutableFloatSignal : FloatSignal {
    operator fun set(index: Int, value: Float)
    fun add(index: Int, value: Float) = set(index, get(index) + value)
}

class ArrayFloatSignal(val data: FloatArray) : MutableFloatSignal {
    override val size: Int get() = data.size
    override fun get(index: Int) = data[index]
    override fun set(index: Int, value: Float) { data[index] = value }
}

/**
 * Arreglo de floats respaldado por un archivo mapeado en memoria (fuera del heap de Java).
 *
 * Una canción de 4 min a 44,1 kHz ocupa ~47 MB por canal; mantener la mezcla y las
 * fuentes separadas en el heap superaría el límite típico de una app Android
 * (256-512 MB). El sistema operativo pagina estos archivos según la memoria disponible.
 */
class MappedFloatArray private constructor(
    private val file: File,
    private val raf: RandomAccessFile,
    private val buffer: FloatBuffer,
    override val size: Int,
    private val deleteOnClose: Boolean,
) : MutableFloatSignal, Closeable {

    override fun get(index: Int): Float = buffer.get(index)
    override fun set(index: Int, value: Float) { buffer.put(index, value) }

    fun fill(value: Float) {
        for (i in 0 until size) buffer.put(i, value)
    }

    override fun close() {
        raf.close()
        // En Android/Linux un archivo mapeado se puede borrar (unlink). En Windows (pruebas
        // JVM) el borrado falla hasta que el GC libera el mapeo: se difiere al salir.
        if (deleteOnClose && !file.delete()) file.deleteOnExit()
    }

    companion object {
        /** Crea un arreglo nuevo (relleno con ceros) en un archivo temporal. */
        fun create(directory: File, name: String, size: Int): MappedFloatArray {
            require(size >= 0)
            directory.mkdirs()
            val file = File(directory, name)
            val raf = RandomAccessFile(file, "rw")
            raf.setLength(size.toLong() * 4)
            return map(file, raf, size, deleteOnClose = true)
        }

        /** Abre un archivo de floats little-endian ya escrito (p. ej. por el decodificador). */
        fun open(file: File, size: Int, deleteOnClose: Boolean): MappedFloatArray {
            val raf = RandomAccessFile(file, "rw")
            require(raf.length() >= size.toLong() * 4) { "Archivo más corto que $size floats: $file" }
            return map(file, raf, size, deleteOnClose)
        }

        private fun map(file: File, raf: RandomAccessFile, size: Int, deleteOnClose: Boolean): MappedFloatArray {
            val bytes = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, size.toLong() * 4)
            bytes.order(ByteOrder.LITTLE_ENDIAN)
            return MappedFloatArray(file, raf, bytes.asFloatBuffer(), size, deleteOnClose)
        }
    }
}
