package com.tacticssim.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.tacticssim.model.CommentaryLanguage
import java.util.UUID

/**
 * Speaks commentary lines aloud using Android's built-in TextToSpeech engine
 * (works fully offline once the device's English voice data is installed —
 * no API key, no network call, no extra dependency).
 *
 * Lines are queued (QUEUE_ADD) so they play back-to-back instead of cutting
 * each other off, and [onSpeakingChanged] fires around each utterance so the
 * caller can duck the stadium ambience bed while the commentator is talking.
 */
class CommentaryVoice(
    context: Context,
    private val language: CommentaryLanguage = CommentaryLanguage.DEFAULT,
    private val onSpeakingChanged: (Boolean) -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    var enabled: Boolean = true

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val result = engine.setLanguage(language.locale)
                ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                pickCommentatorVoice(engine)
                engine.setPitch(0.95f)
                engine.setSpeechRate(1.08f) // fast, energetic delivery like a live commentator
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeakingChanged(true)
                    }
                    override fun onDone(utteranceId: String?) {
                        onSpeakingChanged(false)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onSpeakingChanged(false)
                    }
                })
            } else {
                ready = false
            }
        }
    }

    /** Prefer a voice matching the chosen language/locale if the engine offers more
     * than one for it (e.g. multiple en-* or es-* voices); falls back to whatever
     * setLanguage() already selected. */
    private fun pickCommentatorVoice(engine: TextToSpeech) {
        try {
            val candidates: Set<Voice> = engine.voices ?: return
            val matches = candidates.filter {
                it.locale.language == language.locale.language && !it.isNetworkConnectionRequired
            }
            val preferred = matches.firstOrNull { it.locale.country == language.locale.country }
                ?: matches.firstOrNull()
            preferred?.let { engine.voice = it }
        } catch (e: Exception) {
            // Voice enumeration isn't supported on every device/engine — default voice is fine.
        }
    }

    fun speak(text: String) {
        if (!enabled || !ready) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
