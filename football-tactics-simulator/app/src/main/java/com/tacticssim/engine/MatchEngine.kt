package com.tacticssim.engine

import com.tacticssim.model.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Rule-based (non-physics, non-ML) match engine.
 *
 * Every tick (roughly one game-second):
 *  1. The team in possession looks for the best pass option (nearest open
 *     teammate biased toward forward/central lanes), tuned by that team's
 *     [Strategy] and, if AI-controlled, its [AiDifficulty] mistake rate.
 *  2. Every player drifts toward a target point derived from their formation
 *     zone, shifted by ball position and strategy's defensive-line bias.
 *  3. If the ball is in a dangerous area, there's a chance of a shot; xG is
 *     estimated from distance + angle, and a goal is rolled against it — but
 *     a goal is held as [pendingGoal] until the UI resolves the VAR check via
 *     [resolveVar], matching the "every goal must be confirmed" requirement.
 *
 * This stays rule-based/legible on purpose (see README) rather than physics
 * or ML: the goal is to expose tactical cause-and-effect, not simulate
 * biomechanics.
 */
class MatchEngine(
    private val home: Team,
    private val away: Team,
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

    /** Set when a shot resolves as a goal; cleared once [resolveVar] is called.
     * While non-null, the UI should pause simulation and show a VAR overlay. */
    var pendingGoal: MatchEvent? = null
        private set

    fun currentMinute() = secondsElapsed / 60
    fun currentSecond() = secondsElapsed % 60
    fun homeTeam() = home
    fun awayTeam() = away

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

    /** Advances the simulation by one tick, returns any notable event produced.
     * Returns null (and does nothing) while a goal is pending VAR review. */
    fun step(): MatchEvent? {
        if (pendingGoal != null) return null

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

        if (event?.type == EventType.GOAL) {
            // Hold the goal behind VAR rather than applying it to the scoreline yet.
            pendingGoal = event
            val review = MatchEvent(EventType.VAR_REVIEW, event.minute, event.second, event.side, event.playerId, location = event.location, xg = event.xg)
            recordFrame(review)
            return review
        }

        event?.let { stats.apply(it) }
        recordFrame(event)
        return event
    }

    /** Resolves a pending goal. [confirmed] true applies the goal to the scoreline
     * and clears possession as normal; false disallows it and restarts with the
     * defending goalkeeper in possession, same as a save. */
    fun resolveVar(confirmed: Boolean): MatchEvent {
        val goal = pendingGoal ?: error("resolveVar called with no pending goal")
        pendingGoal = null

        return if (confirmed) {
            stats.apply(goal)
            recordFrame(goal)
            goal
        } else {
            val defendingTeam = if (goal.side == Side.HOME) away else home
            possession = if (goal.side == Side.HOME) Side.AWAY else Side.HOME
            carrierId = defendingTeam.players.firstOrNull { it.position == Position.GK }?.id
                ?: defendingTeam.players.firstOrNull()?.id
            val disallowed = MatchEvent(EventType.GOAL_DISALLOWED, currentMinute(), currentSecond(), goal.side, goal.playerId, location = goal.location, xg = goal.xg)
            recordFrame(disallowed)
            disallowed
        }
    }

    private fun decayStamina(team: Team) {
        for (p in team.players) {
            val fatigueRate = if (secondHalf) 0.00028f else 0.00018f
            p.stamina = (p.stamina - fatigueRate).coerceAtLeast(0.55f)
        }
    }

    private fun aiMistakeChance(team: Team): Float =
        (team.controller as? Controller.Ai)?.difficulty?.mistakeRate ?: 0f

    private fun reactionMultiplier(team: Team): Float =
        (team.controller as? Controller.Ai)?.difficulty?.reactionBias ?: 1f

    private fun moveTeamTowardShape(team: Team, ballPos: Point, attacking: Boolean) {
        val linePush = team.strategy.defensiveLineBias
        for (p in team.players) {
            val pull = (if (attacking) 0.12f else 0.08f) + linePush * 0.1f
            val ballBias = Point(
                (ballPos.x - p.homeZone.x) * pull,
                (ballPos.y - p.homeZone.y) * pull * 0.6f
            )
            val target = Point(
                (p.homeZone.x + ballBias.x).coerceIn(0.02f, 0.98f),
                (p.homeZone.y + ballBias.y).coerceIn(0.03f, 0.97f)
            )
            // Per-role pace: forwards/wingers close ground faster than markers
            // holding a defensive line, so the whole team doesn't glide at one
            // identical robotic speed.
            val roleAgility = when (p.position.group) {
                RoleGroup.ATTACK -> 1.15f
                RoleGroup.MIDFIELD -> 1.0f
                RoleGroup.DEFENSE -> 0.9f
                RoleGroup.GOALKEEPER -> 0.55f
            }
            val baseSpeed = 0.06f * p.stamina * reactionMultiplier(team) * roleAgility

            val dist = p.current.distanceTo(target)
            // Ease toward the target: full pace when far away, tapering off on
            // approach (a sprint that settles into a jog) instead of a constant
            // speed that then teleports the last inch — reads as far more natural.
            val eased = baseSpeed * (0.55f + 0.45f * (dist / 0.10f).coerceIn(0f, 1f))
            // A touch of per-tick jitter (position-seeded, so it's stable in
            // direction rather than flickering) keeps a settled player from
            // looking frozen in place.
            val jitterSeed = p.id.hashCode() + tick
            val jitter = 0.0025f * roleAgility
            val jitterX = (((jitterSeed * 1103515245 + 12345) ushr 16) % 200 - 100) / 100f * jitter
            val jitterY = (((jitterSeed * 214013 + 2531011) ushr 16) % 200 - 100) / 100f * jitter

            val nextPoint = stepToward(p.current, target, eased)
            p.current = Point(
                (nextPoint.x + jitterX).coerceIn(0.01f, 0.99f),
                (nextPoint.y + jitterY).coerceIn(0.02f, 0.98f)
            )
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
        val attemptChance = (0.35f + team.strategy.tempoBias).coerceIn(0.15f, 0.6f)
        if (rng.nextFloat() > attemptChance) return null
        val carrier = team.players.firstOrNull { it.id == carrierId } ?: return null

        val forwardDir = if (possession == Side.HOME) 1f else -1f
        val candidates = team.players.filter { it.id != carrier.id }
        if (candidates.isEmpty()) return null

        val makesMistake = rng.nextFloat() < aiMistakeChance(team)
        val best = if (makesMistake) {
            candidates.random(rng) // AI difficulty: a sloppy, non-optimal pass
        } else {
            val riskTolerance = team.strategy.passRiskBias
            candidates.maxByOrNull { mate ->
                val forwardProgress = (mate.current.x - carrier.current.x) * forwardDir
                val dist = carrier.current.distanceTo(mate.current)
                val distPenalty = (if (dist > 0.45f) dist * 2f else dist * 0.3f) * (1f - riskTolerance).coerceAtLeast(0.4f)
                forwardProgress - distPenalty
            }
        } ?: return null

        val target = best.current

        // A pass sprayed right out toward the touchline sometimes runs out of
        // play rather than always finding the target cleanly — awarded as a
        // throw-in to the defending side, taken from where it went out.
        val wideMistake = makesMistake || rng.nextFloat() < 0.06f
        if (wideMistake && (target.y < 0.07f || target.y > 0.93f) && rng.nextFloat() < 0.4f) {
            val outY = if (target.y < 0.07f) 0.02f else 0.98f
            val outPoint = Point(target.x.coerceIn(0.04f, 0.96f), outY)
            val defendingTeam = if (possession == Side.HOME) away else home
            val thrower = defendingTeam.players.minByOrNull { it.current.distanceTo(outPoint) } ?: return null
            possession = oppositeOf(possession)
            carrierId = thrower.id
            ball = outPoint
            return MatchEvent(EventType.THROW_IN, currentMinute(), currentSecond(), thrower.side, thrower.id, location = outPoint)
        }

        ball = target
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
        if (!isGoal) {
            val attackingSide = possession
            // Roughly a third of blocked/deflected efforts from inside the box
            // go behind for a corner rather than sitting comfortably for the
            // keeper — matches how a broadcast would actually describe it.
            val toCorner = isInBox(carrier.current, possession) && rng.nextFloat() < 0.33f
            if (toCorner) {
                val cornerY = if (carrier.current.y < 0.5f) 0.02f else 0.98f
                val cornerX = if (attackingSide == Side.HOME) 0.99f else 0.01f
                val cornerPoint = Point(cornerX, cornerY)
                val attackingTeam = if (attackingSide == Side.HOME) home else away
                val taker = attackingTeam.players.minByOrNull { it.current.distanceTo(cornerPoint) } ?: carrier
                carrierId = taker.id
                ball = cornerPoint
                return MatchEvent(EventType.CORNER, currentMinute(), currentSecond(), attackingSide, taker.id, location = cornerPoint, xg = xg)
            }
            possession = oppositeOf(possession) // turnover on miss/save
            val nextCarrierTeam = if (possession == Side.HOME) home else away
            carrierId = nextCarrierTeam.players.firstOrNull { it.position == Position.GK }?.id
                ?: nextCarrierTeam.players.firstOrNull()?.id
            return MatchEvent(EventType.SHOT, currentMinute(), currentSecond(), carrier.side, carrier.id, location = carrier.current, xg = xg)
        }
        return MatchEvent(EventType.GOAL, currentMinute(), currentSecond(), carrier.side, carrier.id, location = carrier.current, xg = xg)
    }

    private fun maybeLoseBall(attacking: Team, defending: Team): MatchEvent? {
        if (rng.nextFloat() > 0.06f) return null // occasional tackle / interception
        val carrier = attacking.players.firstOrNull { it.id == carrierId } ?: return null
        val tackler = defending.players.minByOrNull { it.current.distanceTo(carrier.current) } ?: return null

        // Not every challenge is clean — roughly one in five is given as a foul,
        // in which case the attacking side keeps the ball for a free kick
        // rather than losing possession to the tackle.
        val isFoul = rng.nextFloat() < 0.2f
        if (isFoul) {
            return MatchEvent(EventType.FREE_KICK, currentMinute(), currentSecond(), carrier.side, carrier.id, tackler.id, carrier.current)
        }

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
fun buildTeam(
    side: Side,
    name: String,
    formation: Formation,
    kit: Kit,
    strategy: Strategy,
    controller: Controller
): Team {
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
    return Team(side, name, kit, strategy, controller, players)
}
