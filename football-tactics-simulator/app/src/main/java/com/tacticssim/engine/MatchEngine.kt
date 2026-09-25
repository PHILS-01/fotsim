package com.tacticssim.engine

import com.tacticssim.model.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Rule-based (non-physics, non-ML) match engine.
 *
 * Every tick (roughly one game-second):
 *  1. The team in possession looks for the best pass option (nearest open
 *     teammate biased toward forward/central lanes).
 *  2. Every player drifts toward a target point derived from their formation
 *     zone, shifted by ball position (defensive shape compresses/expands)
 *     and their stamina.
 *  3. If the ball is in a dangerous area and possession holds, there's a
 *     chance the player shoots; xG is estimated from distance + angle to
 *     goal, and a goal is rolled probabilistically against that xG.
 *
 * This is intentionally legible/tunable rather than "realistic physics" —
 * see the product brief: the goal is to expose tactical cause-and-effect,
 * not to simulate biomechanics.
 */
class MatchEngine(
    private val home: Team,
    private val away: Team,
    private val homeFormation: Formation,
    private val awayFormation: Formation,
    seed: Long = System.currentTimeMillis()
) {
    private val rng = Random(seed)
    val stats = MatchStats()
    val frames = mutableListOf<MatchFrame>()

    private var ball = Point(0.5f, 0.5f)
    private var possession: Side = Side.HOME
    private var carrierId: String? = null
    private var tick = 0
    private var secondsElapsed = 0
    private var secondHalf = false

    companion object {
        const val TICKS_PER_SECOND = 1 // 1 tick == 1 simulated game second
    }

    fun currentMinute() = secondsElapsed / 60
    fun currentSecond() = secondsElapsed % 60

    fun kickoff(): MatchEvent {
        ball = Point(0.5f, 0.5f)
        possession = Side.HOME
        carrierId = home.players.firstOrNull { it.position != Position.GK }?.id
        val ev = MatchEvent(EventType.KICKOFF, currentMinute(), currentSecond(), Side.HOME)
        stats.apply(ev)
        recordFrame(ev)
        return ev
    }

    /** Swaps ends at halftime: mirror every player's home zone left/right. */
    fun startSecondHalf(): MatchEvent {
        secondHalf = true
        mirrorZones(home)
        mirrorZones(away)
        ball = Point(0.5f, 0.5f)
        possession = Side.AWAY
        carrierId = away.players.firstOrNull { it.position != Position.GK }?.id
        val ev = MatchEvent(EventType.HALFTIME, currentMinute(), currentSecond(), Side.HOME)
        recordFrame(ev)
        return ev
    }

    private fun mirrorZones(team: Team) {
        for (p in team.players) {
            p.current = Point(1f - p.current.x, p.current.y)
        }
    }

    /** Advances the simulation by one tick, returns any notable event produced. */
    fun step(): MatchEvent? {
        tick++
        secondsElapsed++

        val attackingTeam = if (possession == Side.HOME) home else away
        val defendingTeam = if (possession == Side.HOME) away else home

        decayStamina(home)
        decayStamina(away)

        moveTeamTowardShape(attackingTeam, ball, attacking = true)
        moveTeamTowardShape(defendingTeam, ball, attacking = false)

        var event = maybeAttemptPass(attackingTeam)
        if (event == null) {
            event = maybeAttemptShot(attackingTeam)
        }
        if (event == null) {
            event = maybeLoseBall(attackingTeam, defendingTeam)
        }

        event?.let { stats.apply(it) }
        recordFrame(event)
        return event
    }

    private fun decayStamina(team: Team) {
        for (p in team.players) {
            val fatigueRate = if (secondHalf) 0.00028f else 0.00018f
            p.stamina = (p.stamina - fatigueRate).coerceAtLeast(0.55f)
        }
    }

    private fun moveTeamTowardShape(team: Team, ballPos: Point, attacking: Boolean) {
        for (p in team.players) {
            val pull = if (attacking) 0.12f else 0.08f // attackers push further from their zone
            val ballBias = Point(
                (ballPos.x - p.homeZone.x) * pull,
                (ballPos.y - p.homeZone.y) * pull * 0.6f
            )
            val target = Point(
                (p.homeZone.x + ballBias.x).coerceIn(0.02f, 0.98f),
                (p.homeZone.y + ballBias.y).coerceIn(0.03f, 0.97f)
            )
            val speed = 0.06f * p.stamina
            p.current = stepToward(p.current, target, speed)
        }
    }

    private fun stepToward(from: Point, to: Point, maxStep: Float): Point {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= maxStep || dist == 0f) return to
        val ratio = maxStep / dist
        return Point(from.x + dx * ratio, from.y + dy * ratio)
    }

    private fun maybeAttemptPass(team: Team): MatchEvent? {
        if (rng.nextFloat() > 0.35f) return null // not every tick is a pass
        val carrier = team.players.firstOrNull { it.id == carrierId } ?: return null

        val forwardDir = if (possession == Side.HOME) 1f else -1f
        val candidates = team.players.filter { it.id != carrier.id }
        if (candidates.isEmpty()) return null

        // Score teammates by: forward progress, central lanes, and proximity (not too far).
        val best = candidates.maxByOrNull { mate ->
            val forwardProgress = (mate.current.x - carrier.current.x) * forwardDir
            val dist = carrier.current.distanceTo(mate.current)
            val distPenalty = if (dist > 0.45f) dist * 2f else dist * 0.3f
            forwardProgress - distPenalty
        } ?: return null

        ball = best.current
        carrierId = best.id
        val dangerZone = isInBox(ball, possession)
        if (dangerZone) {
            stats.apply(MatchEvent(EventType.CHANCE_CREATED, currentMinute(), currentSecond(), possession, best.id, location = ball))
        }
        return MatchEvent(
            EventType.PASS,
            currentMinute(), currentSecond(),
            possession, carrier.id, best.id, ball
        )
    }

    private fun isInBox(p: Point, side: Side): Boolean {
        val attackingRight = side == Side.HOME
        return if (attackingRight) p.x > 0.82f && p.y in 0.22f..0.78f
        else p.x < 0.18f && p.y in 0.22f..0.78f
    }

    private fun maybeAttemptShot(team: Team): MatchEvent? {
        val carrier = team.players.firstOrNull { it.id == carrierId } ?: return null
        if (!isInBox(carrier.current, possession) && rng.nextFloat() > 0.02f) return null
        if (rng.nextFloat() > 0.18f) return null // only some ticks in the box trigger a shot

        val goalPoint = if (possession == Side.HOME) Point(1f, 0.5f) else Point(0f, 0.5f)
        val dist = carrier.current.distanceTo(goalPoint)
        val angleFactor = 1f - abs(carrier.current.y - 0.5f) * 1.4f
        val xg = (0.5 * (1.0 - dist.toDouble()) * angleFactor.toDouble().coerceIn(0.05, 1.0))
            .coerceIn(0.02, 0.85)

        val isGoal = rng.nextDouble() < xg
        possession = if (isGoal) possession else oppositeOf(possession) // turnover on miss/save
        val nextCarrierTeam = if (possession == Side.HOME) home else away
        carrierId = nextCarrierTeam.players.firstOrNull { it.position == Position.GK }?.id
            ?: nextCarrierTeam.players.firstOrNull()?.id

        return if (isGoal) {
            MatchEvent(EventType.GOAL, currentMinute(), currentSecond(), carrier.side, carrier.id, location = carrier.current, xg = xg)
        } else {
            MatchEvent(EventType.SHOT, currentMinute(), currentSecond(), carrier.side, carrier.id, location = carrier.current, xg = xg)
        }
    }

    private fun maybeLoseBall(attacking: Team, defending: Team): MatchEvent? {
        if (rng.nextFloat() > 0.06f) return null // occasional tackle / interception
        val carrier = attacking.players.firstOrNull { it.id == carrierId } ?: return null
        val tackler = defending.players.minByOrNull { it.current.distanceTo(carrier.current) } ?: return null

        possession = oppositeOf(possession)
        carrierId = tackler.id
        ball = tackler.current
        return MatchEvent(EventType.TACKLE, currentMinute(), currentSecond(), tackler.side, tackler.id, carrier.id, tackler.current)
    }

    private fun oppositeOf(side: Side) = if (side == Side.HOME) Side.AWAY else Side.HOME

    /** Returns the home team's players with their `current` point overridden by the
     * given position map — used by the UI to render either live or scrubbed-replay frames. */
    fun homeTeamSnapshot(positions: Map<String, Point>): List<Player> =
        home.players.map { p -> p.copy(current = positions[p.id] ?: p.current) }

    fun awayTeamSnapshot(positions: Map<String, Point>): List<Player> =
        away.players.map { p -> p.copy(current = positions[p.id] ?: p.current) }

    private fun recordFrame(event: MatchEvent?): MatchFrame {
        val frame = MatchFrame(
            tick = tick,
            minute = currentMinute(),
            second = currentSecond(),
            ball = ball,
            homePositions = home.players.associate { it.id to it.current },
            awayPositions = away.players.associate { it.id to it.current },
            event = event
        )
        frames.add(frame)
        return frame
    }
}

/** Builds a Team of Players placed at a formation's home zones, mirrored for the AWAY side. */
fun buildTeam(side: Side, name: String, formation: Formation): Team {
    val players = formation.slots.mapIndexed { index, slot ->
        val zone = if (side == Side.AWAY) Point(1f - slot.zone.x, slot.zone.y) else slot.zone
        Player(
            id = "${side.name}_${index}_${slot.position.name}",
            side = side,
            position = slot.position,
            homeZone = zone,
            current = zone
        )
    }.toMutableList()
    return Team(side, name, players)
}
