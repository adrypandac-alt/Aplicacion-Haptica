package com.tesis.audiotohaptics.transcription

/**
 * Posprocesamiento de Basic Pitch (basic_pitch.note_creation.output_to_notes_polyphonic),
 * con los valores por defecto de basic_pitch.inference.predict. Port de
 * tools/model_export/reference_basic_pitch.py, validado contra el CSV del prototipo
 * (F1 = 1,0 con la misma entrada).
 */
internal object NoteCreation {
    const val N_PITCHES = 88
    const val MIDI_OFFSET = 21
    private const val MAX_FREQ_IDX = 87
    private const val ENERGY_TOL = 11

    data class FrameNote(val startFrame: Int, val endFrame: Int, val pitch: Int, val amplitude: Double)

    /**
     * @param frames activaciones de nota [nFrames * 88] (fila = frame).
     * @param onsets activaciones de inicio [nFrames * 88].
     */
    fun notesPolyphonic(
        frames: FloatArray,
        onsets: FloatArray,
        nFrames: Int,
        onsetThreshold: Double = 0.5,
        frameThreshold: Double = 0.3,
        minNoteLength: Int = 11,
        melodiaTrick: Boolean = true,
    ): List<FrameNote> {
        val p = N_PITCHES
        val onsetMat = inferredOnsets(onsets, frames, nFrames)

        // scipy.signal.argrelmax(axis=0): máximo local estricto en el tiempo, sin bordes.
        val candidates = ArrayList<Pair<Int, Int>>()
        for (t in 1 until nFrames - 1) {
            for (f in 0 until p) {
                val v = onsetMat[t * p + f]
                if (v > onsetMat[(t - 1) * p + f] && v > onsetMat[(t + 1) * p + f] && v >= onsetThreshold) {
                    candidates.add(t to f)
                }
            }
        }
        val remaining = DoubleArray(nFrames * p) { frames[it].toDouble() }
        val notes = ArrayList<FrameNote>()

        // np.where recorre en orden (t, f); Basic Pitch lo invierte para ir hacia atrás en el tiempo.
        for (idx in candidates.indices.reversed()) {
            val (start, f) = candidates[idx]
            if (start >= nFrames - 1) continue
            var i = start + 1
            var k = 0
            while (i < nFrames - 1 && k < ENERGY_TOL) {
                k = if (remaining[i * p + f] < frameThreshold) k + 1 else 0
                i++
            }
            i -= k
            if (i - start <= minNoteLength) continue
            for (t in start until i) {
                remaining[t * p + f] = 0.0
                if (f < MAX_FREQ_IDX) remaining[t * p + f + 1] = 0.0
                if (f > 0) remaining[t * p + f - 1] = 0.0
            }
            notes.add(FrameNote(start, i, f + MIDI_OFFSET, meanColumn(frames, start, i, f)))
        }

        if (melodiaTrick) {
            while (true) {
                // np.argmax: primer máximo en orden fila-mayor.
                var best = -1
                var bestValue = Double.NEGATIVE_INFINITY
                for (j in remaining.indices) {
                    if (remaining[j] > bestValue) { bestValue = remaining[j]; best = j }
                }
                if (bestValue <= frameThreshold) break
                val iMid = best / p
                val f = best % p
                remaining[best] = 0.0

                var i = iMid + 1
                var k = 0
                while (i < nFrames - 1 && k < ENERGY_TOL) {
                    k = if (remaining[i * p + f] < frameThreshold) k + 1 else 0
                    clear(remaining, i, f)
                    i++
                }
                val iEnd = i - 1 - k

                i = iMid - 1
                k = 0
                while (i > 0 && k < ENERGY_TOL) {
                    k = if (remaining[i * p + f] < frameThreshold) k + 1 else 0
                    clear(remaining, i, f)
                    i--
                }
                val iStart = i + 1 + k
                if (iEnd - iStart <= minNoteLength) continue
                notes.add(FrameNote(iStart, iEnd, f + MIDI_OFFSET, meanColumn(frames, iStart, iEnd, f)))
            }
        }
        return notes
    }

    /** get_infered_onsets(n_diff=2): combina los onsets del modelo con las subidas de los frames. */
    internal fun inferredOnsets(onsets: FloatArray, frames: FloatArray, nFrames: Int): DoubleArray {
        val p = N_PITCHES
        val diff = DoubleArray(nFrames * p)
        for (t in 0 until nFrames) {
            for (f in 0 until p) {
                if (t < 2) { diff[t * p + f] = 0.0; continue }
                val cur = frames[t * p + f].toDouble()
                val d1 = cur - frames[(t - 1) * p + f]
                val d2 = cur - frames[(t - 2) * p + f]
                diff[t * p + f] = maxOf(0.0, minOf(d1, d2))
            }
        }
        var maxOnset = Double.NEGATIVE_INFINITY
        for (v in onsets) if (v > maxOnset) maxOnset = v.toDouble()
        var maxDiff = Double.NEGATIVE_INFINITY
        for (v in diff) if (v > maxDiff) maxDiff = v
        return DoubleArray(nFrames * p) { j ->
            val scaled = if (maxDiff > 0) maxOnset * diff[j] / maxDiff else Double.NaN
            // np.max([onsets, nan]) propaga NaN; con maxDiff == 0 Basic Pitch también produce NaN.
            if (scaled.isNaN()) Double.NaN else maxOf(onsets[j].toDouble(), scaled)
        }
    }

    private fun clear(remaining: DoubleArray, i: Int, f: Int) {
        val p = N_PITCHES
        remaining[i * p + f] = 0.0
        if (f < MAX_FREQ_IDX) remaining[i * p + f + 1] = 0.0
        if (f > 0) remaining[i * p + f - 1] = 0.0
    }

    private fun meanColumn(frames: FloatArray, from: Int, to: Int, f: Int): Double {
        var s = 0.0
        for (t in from until to) s += frames[t * N_PITCHES + f]
        return s / (to - from)
    }
}
