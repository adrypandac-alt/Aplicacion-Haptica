package com.leff.midi.event

abstract class MidiEvent

open class NoteOn(val note: Int, val velocity: Int) : MidiEvent()
