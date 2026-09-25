package com.tacticssim.engine

import android.content.Context
import com.tacticssim.model.CommentaryLanguage

/** Local (SharedPreferences) app settings — currently just the commentary language,
 * chosen under Settings on the setup screen and remembered for next time. */
object SettingsStore {
    private const val PREFS = "tacticssim_settings"
    private const val KEY_LANGUAGE = "commentary_language"

    fun commentaryLanguage(context: Context): CommentaryLanguage {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null) ?: return CommentaryLanguage.DEFAULT
        return try {
            CommentaryLanguage.valueOf(name)
        } catch (e: IllegalArgumentException) {
            CommentaryLanguage.DEFAULT
        }
    }

    fun setCommentaryLanguage(context: Context, language: CommentaryLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }
}
