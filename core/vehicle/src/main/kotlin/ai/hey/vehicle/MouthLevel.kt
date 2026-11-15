package ai.hey.vehicle

import kotlin.math.sqrt

/** Mouth openness 0..3 (`mouth_0`..`mouth_3`). */
const val MOUTH_MAX = 3

/**
 * Loudness -> mouth level. [t1] <= [t2] <= [t3] are the lower edges of levels 1, 2 and 3; below [t1] the mouth is shut (0).
 * For server PCM the loudness is the RMS of a 30 ms window as a fraction of full scale (0..1); the Pleos TTS fallback uses
 * its own thresholds because `onUpdatedRms` has another scale. Both are plain values so they can be tuned without code.
 */
data class MouthThresholds(val t1: Double, val t2: Double, val t3: Double) {
    init {
        require(t1 <= t2 && t2 <= t3) { "thresholds must not decrease" }
    }

    fun level(loudness: Double): Int = when {
        loudness >= t3 -> 3
        loudness >= t2 -> 2
        loudness >= t1 -> 1
        else -> 0
    }

    companion object {
        /** Server PCM: RMS of a 30 ms window as a fraction of full scale. Speech from the character voice sits around 0.02..0.2. */
        val PCM = MouthThresholds(t1 = 0.02, t2 = 0.06, t3 = 0.12)

        /**
         * Pleos `onUpdatedRms`: the value is on a 16-bit sample scale, not 0..1. Starting edges,
         * to be tuned from the raw values the engine logs (`pleos rms raw=`).
         */
        val PLEOS = MouthThresholds(t1 = 800.0, t2 = 2500.0, t3 = 5000.0)
    }
}

/** Window the mouth follows: 30 ms of audio. */
const val MOUTH_WINDOW_MS = 30

object PcmRms {
    /** RMS of s16le mono samples in `[offset, offset + length)` as a fraction of full scale (0..1). A trailing odd byte is ignored. */
    fun rms(pcm: ByteArray, offset: Int, length: Int): Double {
        val samples = length / 2
        if (samples <= 0) return 0.0
        var sum = 0.0
        var i = offset
        repeat(samples) {
            val s = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8) // sign comes from the high byte
            sum += s.toDouble() * s
            i += 2
        }
        return sqrt(sum / samples) / 32768.0
    }

    /** Bytes of one 30 ms window at [sampleRate] (mono, 16-bit). */
    fun windowBytes(sampleRate: Int): Int = (sampleRate * MOUTH_WINDOW_MS / 1000) * 2
}
