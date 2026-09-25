package com.tacticssim.model

/** Normalized pitch coordinate: x,y in [0,1]. (0,0) = own bottom-left corner. */
data class Point(val x: Float, val y: Float) {
    fun distanceTo(other: Point): Float {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

enum class Side { HOME, AWAY }

/** A team's chosen kit: shirt color plus a contrasting trim/number color,
 * so Team A and Team B are always visually distinct on the pitch — this is
 * what fixes the "both teams look the same" color-coding bug. */
data class Kit(val shirtHex: String, val trimHex: String, val label: String) {
    companion object {
        val PRESETS = listOf(
            Kit("#D32F2F", "#FFFFFF", "Red"),
            Kit("#1565C0", "#FFEB3B", "Blue"),
            Kit("#212121", "#FFFFFF", "Black"),
            Kit("#FFFFFF", "#212121", "White"),
            Kit("#2E7D32", "#FFFFFF", "Green"),
            Kit("#F57F17", "#212121", "Amber"),
            Kit("#6A1B9A", "#FFFFFF", "Purple"),
            Kit("#00838F", "#FFFFFF", "Teal")
        )
        /** Unlocked via the credit system — see ProgressStore. */
        val UNLOCKABLE = listOf(
            Kit("#FFD700", "#212121", "Gold"),
            Kit("#EC407A", "#212121", "Pink Flash")
        )
    }
}

/**
 * A team's chosen tactical approach. Tunes the match engine's aggression,
 * risk, and defensive line rather than being purely cosmetic.
 */
enum class Strategy(val label: String, val passRiskBias: Float, val tempoBias: Float, val defensiveLineBias: Float) {
    POSSESSION("Possession", passRiskBias = -0.10f, tempoBias = -0.05f, defensiveLineBias = 0.05f),
    COUNTER_ATTACK("Counter-Attack", passRiskBias = 0.10f, tempoBias = 0.08f, defensiveLineBias = -0.10f),
    HIGH_PRESS("High Press", passRiskBias = 0.05f, tempoBias = 0.10f, defensiveLineBias = 0.15f),
    PARK_THE_BUS("Park the Bus", passRiskBias = -0.15f, tempoBias = -0.12f, defensiveLineBias = -0.20f)
}

/** Who controls a side during the match. Online is modeled here so the engine
 * and UI are wired for it, but no networking is implemented yet — see README. */
sealed class Controller {
    object Human : Controller()
    data class Ai(val difficulty: AiDifficulty) : Controller()
    object OnlineRemote : Controller() // reserved for future networked opponent
}

enum class AiDifficulty(val label: String, val mistakeRate: Float, val reactionBias: Float) {
    MEDIUM("Medium", mistakeRate = 0.10f, reactionBias = 0.85f),
    HARD("Hard", mistakeRate = 0.04f, reactionBias = 1.0f)
}

/**
 * A player on the board. [homeZone] is the formation slot they are anchored to;
 * [current] is where the engine has actually placed them this tick.
 * [stamina] in [0,1], decays over the match and slows movement / raises error rate.
 */
data class Player(
    val id: String,
    val side: Side,
    val position: Position,
    val homeZone: Point,
    var current: Point,
    var stamina: Float = 1f
)

data class Team(
    val side: Side,
    val name: String,
    val kit: Kit,
    val strategy: Strategy,
    val controller: Controller,
    val players: MutableList<Player>
)
