package com.tacticssim.audio

import android.content.Context
import android.media.MediaPlayer
import com.tacticssim.R

/**
 * Loops a stadium ambience track for the duration of the match, and can play
 * one-shot whistle stings for kickoff / halftime / fulltime.
 *
 * Drop your own royalty-free audio into res/raw as:
 *   stadium_ambience.mp3  (looping crowd noise bed)
 *   whistle.mp3           (short one-shot whistle)
 * Until those files are added, calls are safely no-ops (see SKILL / README).
 */
class StadiumSound(private val context: Context) {
    private var ambiencePlayer: MediaPlayer? = null

    companion object {
        private const val FULL_VOLUME = 1.0f
        // Crowd noise ducks under the commentator rather than muting entirely,
        // the way a real match broadcast mixes ambience under the voice track.
        private const val DUCKED_VOLUME = 0.25f
    }

    fun startAmbience() {
        stopAmbience()
        ambiencePlayer = safeCreate(R.raw.stadium_ambience)?.apply {
            isLooping = true
            setVolume(FULL_VOLUME, FULL_VOLUME)
            start()
        }
    }

    fun playWhistle() {
        safeCreate(R.raw.whistle)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }

    /** Duck the ambience bed under commentary, or restore it once the line finishes. */
    fun setDucked(ducked: Boolean) {
        val level = if (ducked) DUCKED_VOLUME else FULL_VOLUME
        ambiencePlayer?.setVolume(level, level)
    }

    fun stopAmbience() {
        ambiencePlayer?.release()
        ambiencePlayer = null
    }

    private fun safeCreate(resId: Int): MediaPlayer? = try {
        MediaPlayer.create(context, resId)
    } catch (e: Exception) {
        null // resource missing — fail silently rather than crash the app
    }
}
