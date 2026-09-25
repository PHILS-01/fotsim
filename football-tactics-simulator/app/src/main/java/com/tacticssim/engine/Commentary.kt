package com.tacticssim.engine

import com.tacticssim.model.Side
import kotlin.random.Random

data class CommentaryLine(val minute: Int, val second: Int, val text: String)

/**
 * Converts MatchEvents into analytical, tactics-flavored commentary lines.
 * Not every event produces a line (passes are throttled) to avoid noise;
 * goals/shots/tackles/kickoff/halftime always do.
 */
class CommentaryGenerator(private val homeName: String, private val awayName: String, seed: Long = System.currentTimeMillis()) {

    private val rng = Random(seed)
    private var ticksSinceFlavorLine = 0

    private fun teamName(side: Side) = if (side == Side.HOME) homeName else awayName

    private val goalLines = listOf(
        "GOAL! %s find the breakthrough — the shape opened up at exactly the right moment.",
        "GOAL for %s! That's a chance built patiently, not by accident.",
        "It's in! %s convert — watch how the space was created before the finish."
    )
    private val shotLines = listOf(
        "%s go for goal — decent xG on that one given the angle.",
        "Effort from %s, straight at the keeper. Low-value chance, more a pressure release.",
        "%s test the goalkeeper from distance — the shape behind the ball looks committed."
    )
    private val passLines = listOf(
        "Switch of play from %s, looking to stretch the back line.",
        "%s work it through midfield, patient in possession.",
        "Third-man run from %s opens a passing lane down the channel.",
        "%s build from the back, inviting the press."
    )
    private val tackleLines = listOf(
        "Dispossessed! %s win it back with a well-timed challenge.",
        "%s cut the passing lane and regain possession.",
        "Turnover — %s pounce on a loose touch."
    )
    private val saveLines = listOf(
        "Good save! The %s goalkeeper holds firm.",
        "Well saved by %s — that stays out."
    )
    private val chanceLines = listOf(
        "That's a real chance created by %s — good movement into the box.",
        "%s find a teammate in a dangerous central area."
    )

    fun forEvent(event: MatchEvent): CommentaryLine? {
        val text: String? = when (event.type) {
            EventType.KICKOFF -> "Kickoff! ${homeName} vs ${awayName} gets underway."
            EventType.HALFTIME -> "Halftime. Teams switch ends — watch how the tactical picture flips with them."
            EventType.FULLTIME -> "Full time."
            EventType.GOAL -> goalLines.random(rng).format(teamName(event.side))
            EventType.SHOT -> if (rng.nextFloat() < 0.6f) shotLines.random(rng).format(teamName(event.side)) else null
            EventType.SAVE -> saveLines.random(rng).format(teamName(event.side))
            EventType.TACKLE -> if (rng.nextFloat() < 0.5f) tackleLines.random(rng).format(teamName(event.side)) else null
            EventType.CHANCE_CREATED -> if (rng.nextFloat() < 0.7f) chanceLines.random(rng).format(teamName(event.side)) else null
            EventType.PASS -> {
                ticksSinceFlavorLine++
                if (ticksSinceFlavorLine >= 60 && rng.nextFloat() < 0.25f) {
                    ticksSinceFlavorLine = 0
                    passLines.random(rng).format(teamName(event.side))
                } else null
            }
        }
        return text?.let { CommentaryLine(event.minute, event.second, it) }
    }
}
