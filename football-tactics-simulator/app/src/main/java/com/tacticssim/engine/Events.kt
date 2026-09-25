package com.tacticssim.engine

import com.tacticssim.model.Point
import com.tacticssim.model.Side

enum class EventType {
    KICKOFF, PASS, SHOT, GOAL, SAVE, TACKLE, HALFTIME, FULLTIME, CHANCE_CREATED,
    VAR_REVIEW, GOAL_DISALLOWED, CORNER, THROW_IN, FREE_KICK
}

data class MatchEvent(
    val type: EventType,
    val minute: Int,
    val second: Int,
    val side: Side,
    val playerId: String? = null,
    val secondPlayerId: String? = null, // e.g. pass target, or tackled player
    val location: Point? = null,
    val xg: Double? = null // populated for SHOT / GOAL events
)

/** One frame of the whole match, stored so the replay scrubber can re-render any tick. */
data class MatchFrame(
    val tick: Int,
    val minute: Int,
    val second: Int,
    val ball: Point,
    val homePositions: Map<String, Point>,
    val awayPositions: Map<String, Point>,
    val event: MatchEvent? = null
)

/** Running totals derived purely from the event log — this is the single source of truth
 * for score, possession, xG, passes and chances, so nothing is tracked twice. */
class MatchStats {
    var homeGoals = 0; private set
    var awayGoals = 0; private set
    var homePasses = 0; private set
    var awayPasses = 0; private set
    var homeXg = 0.0; private set
    var awayXg = 0.0; private set
    var homeChances = 0; private set
    var awayChances = 0; private set
    var homeCorners = 0; private set
    var awayCorners = 0; private set
    var homeFreeKicks = 0; private set
    var awayFreeKicks = 0; private set
    private var homeTouches = 0
    private var awayTouches = 0

    fun apply(event: MatchEvent) {
        when (event.type) {
            EventType.PASS -> {
                if (event.side == Side.HOME) { homePasses++; homeTouches++ } else { awayPasses++; awayTouches++ }
            }
            EventType.GOAL -> {
                if (event.side == Side.HOME) homeGoals++ else awayGoals++
                event.xg?.let { if (event.side == Side.HOME) homeXg += it else awayXg += it }
            }
            EventType.SHOT -> {
                event.xg?.let { if (event.side == Side.HOME) homeXg += it else awayXg += it }
            }
            EventType.CHANCE_CREATED -> {
                if (event.side == Side.HOME) homeChances++ else awayChances++
            }
            EventType.CORNER -> {
                if (event.side == Side.HOME) homeCorners++ else awayCorners++
            }
            EventType.FREE_KICK -> {
                if (event.side == Side.HOME) homeFreeKicks++ else awayFreeKicks++
            }
            else -> { /* no stat impact */ }
        }
    }

    fun possessionPercent(): Pair<Int, Int> {
        val total = (homeTouches + awayTouches).coerceAtLeast(1)
        val home = (homeTouches * 100) / total
        return home to (100 - home)
    }
}
