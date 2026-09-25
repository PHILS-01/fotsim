package com.tacticssim.model

import java.util.Locale

/**
 * Spoken-commentary language, chosen under Settings on the setup screen and
 * persisted (see SettingsStore). Drives both which line bank
 * `CommentaryGenerator` picks from and which locale/voice `CommentaryVoice`
 * asks the on-device TextToSpeech engine for.
 */
enum class CommentaryLanguage(val label: String, val locale: Locale) {
    ENGLISH("English", Locale.US),
    SPANISH("Español", Locale("es", "ES")),
    FRENCH("Français", Locale.FRANCE),
    GERMAN("Deutsch", Locale.GERMANY),
    PORTUGUESE("Português", Locale("pt", "PT")),
    ITALIAN("Italiano", Locale.ITALY);

    companion object {
        val DEFAULT = ENGLISH
    }
}
