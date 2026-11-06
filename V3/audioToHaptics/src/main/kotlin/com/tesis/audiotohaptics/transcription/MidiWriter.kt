package com.tesis.audiotohaptics.transcription

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * Escribe un Standard MIDI File tipo 1 con la misma convención que Basic Pitch
 * (pretty_midi): 220 ticks por negra, 120 BPM, compás 4/4, una pista de notas y
 * note_on con velocidad 0 como fin de nota. A 120 BPM un tick equivale a 1/440 s.
 */
object MidiWriter {
    const val TICKS_PER_BEAT = 220
    const val TEMPO_US_PER_BEAT = 500_000

    fun write(file: File, notes: List<NoteEvent>, program: Int = 4) {
        file.parentFile?.mkdirs()
        file.writeBytes(encode(notes, program))
    }

    fun secondsToTicks(seconds: Double): Int =
        (seconds * TICKS_PER_BEAT * 1_000_000.0 / TEMPO_US_PER_BEAT).roundToInt()

    fun encode(notes: List<NoteEvent>, program: Int = 4): ByteArray {
        val meta = ByteArrayOutputStream().apply {
            writeVar(0); write(byteArrayOf(0xFF.toByte(), 0x51, 0x03))
            write((TEMPO_US_PER_BEAT shr 16) and 0xFF); write((TEMPO_US_PER_BEAT shr 8) and 0xFF); write(TEMPO_US_PER_BEAT and 0xFF)
            writeVar(0); write(byteArrayOf(0xFF.toByte(), 0x58, 0x04, 0x04, 0x02, 0x18, 0x08))
            writeVar(0); write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))
        }.toByteArray()

        // (tick, orden: 0 = off antes que on en el mismo tick, nota, velocidad)
        val events = ArrayList<IntArray>()
        for (n in notes) {
            val on = secondsToTicks(n.startS)
            val off = maxOf(secondsToTicks(n.endS), on + 1)
            events.add(intArrayOf(on, 1, n.pitch, n.velocity.coerceIn(1, 127)))
            events.add(intArrayOf(off, 0, n.pitch, 0))
        }
        events.sortWith(compareBy<IntArray>({ it[0] }, { it[1] }, { it[2] }))
        val track = ByteArrayOutputStream().apply {
            writeVar(0); write(0xC0); write(program and 0x7F)
            var last = 0
            for (e in events) {
                writeVar(e[0] - last); last = e[0]
                write(0x90); write(e[2] and 0x7F); write(e[3] and 0x7F)
            }
            writeVar(1); write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write("MThd".toByteArray()); writeInt32(6)
            writeInt16(1); writeInt16(2); writeInt16(TICKS_PER_BEAT)
            write("MTrk".toByteArray()); writeInt32(meta.size); write(meta)
            write("MTrk".toByteArray()); writeInt32(track.size); write(track)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeVar(value: Int) {
        require(value >= 0)
        var buffer = value and 0x7F
        var v = value ushr 7
        while (v > 0) {
            buffer = (buffer shl 8) or ((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        while (true) {
            write(buffer and 0xFF)
            if (buffer and 0x80 != 0) buffer = buffer ushr 8 else break
        }
    }

    private fun ByteArrayOutputStream.writeInt32(v: Int) {
        write((v ushr 24) and 0xFF); write((v ushr 16) and 0xFF); write((v ushr 8) and 0xFF); write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.writeInt16(v: Int) {
        write((v ushr 8) and 0xFF); write(v and 0xFF)
    }
}
