package com.tacticssim.engine

import android.content.Context
import com.tacticssim.model.Kit

/**
 * Simple local progression system: credits earned from match results unlock
 * bonus kits. This is intentionally local-only (SharedPreferences) — a real
 * online leaderboard/unlock-sync system would ride on the same backend that
 * online multiplayer would need (see README), so it's out of scope for now.
 */
object ProgressStore {
    private const val PREFS = "tacticssim_progress"
    private const val KEY_CREDITS = "credits"
    private const val KEY_UNLOCKED = "unlocked_kits"

    private const val WIN_CREDITS = 30
    private const val DRAW_CREDITS = 10
    private const val LOSS_CREDITS = 5
    const val UNLOCK_THRESHOLD_PER_KIT = 100

    fun credits(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CREDITS, 0)

    fun unlockedKitLabels(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()

    fun isUnlocked(context: Context, kit: Kit): Boolean =
        kit in Kit.PRESETS || kit.label in unlockedKitLabels(context)

    /** Call once at full time. Returns the credits earned this match, and
     * unlocks a new bonus kit for every [UNLOCK_THRESHOLD_PER_KIT] total credits crossed. */
    fun recordResult(context: Context, playerGoals: Int, opponentGoals: Int): Int {
        val earned = when {
            playerGoals > opponentGoals -> WIN_CREDITS
            playerGoals == opponentGoals -> DRAW_CREDITS
            else -> LOSS_CREDITS
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getInt(KEY_CREDITS, 0)
        val after = before + earned
        val editor = prefs.edit().putInt(KEY_CREDITS, after)

        val unlocked = (prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()).toMutableSet()
        val kitsThatShouldBeUnlocked = (after / UNLOCK_THRESHOLD_PER_KIT).coerceAtMost(Kit.UNLOCKABLE.size)
        for (i in 0 until kitsThatShouldBeUnlocked) {
            unlocked.add(Kit.UNLOCKABLE[i].label)
        }
        editor.putStringSet(KEY_UNLOCKED, unlocked)
        editor.apply()

        return earned
    }
}
