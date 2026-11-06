package com.leff.midi

import java.io.InputStream
import java.io.File

class MidiFile(inputStream: InputStream) {
    val tracks: List<MidiTrack> = mutableListOf()
    // Implementación simplificada para el prototipo
}

class MidiTrack {
    val events: List<com.leff.midi.event.MidiEvent> = mutableListOf()
}
