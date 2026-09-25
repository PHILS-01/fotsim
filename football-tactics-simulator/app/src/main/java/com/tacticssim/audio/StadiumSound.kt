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

    fun startAmbience() {
        stopAmbience()
        ambiencePlayer = safeCreate(R.raw.stadium_ambience)?.apply {
            isLooping = true
            start()
        }
    }

    fun playWhistle() {
        safeCreate(R.raw.whistle)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
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
