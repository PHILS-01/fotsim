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
    val players: MutableList<Player>
)
