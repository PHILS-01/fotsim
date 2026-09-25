package com.tacticssim.model

/**
 * A formation is a named list of (Position -> home zone) slots for one team,
 * defined attacking left-to-right on a 0..1 pitch. When a team defends the
 * other way (after halftime, or as the AWAY side), coordinates are mirrored
 * by the engine rather than redefined here.
 */
data class FormationSlot(val position: Position, val zone: Point)

data class Formation(val name: String, val slots: List<FormationSlot>) {
    init {
        require(slots.size == 11) { "Formation $name must have exactly 11 slots, has ${slots.size}" }
    }
}

object Formations {

    val F442 = Formation(
        "4-4-2",
        listOf(
            FormationSlot(Position.GK, Point(0.05f, 0.50f)),
            FormationSlot(Position.RB, Point(0.20f, 0.15f)),
            FormationSlot(Position.RCB, Point(0.18f, 0.38f)),
            FormationSlot(Position.LCB, Point(0.18f, 0.62f)),
            FormationSlot(Position.LB, Point(0.20f, 0.85f)),
            FormationSlot(Position.RM, Point(0.45f, 0.15f)),
            FormationSlot(Position.RCM, Point(0.42f, 0.38f)),
            FormationSlot(Position.LCM, Point(0.42f, 0.62f)),
            FormationSlot(Position.LM, Point(0.45f, 0.85f)),
            FormationSlot(Position.RF, Point(0.75f, 0.38f)),
            FormationSlot(Position.LF, Point(0.75f, 0.62f))
        )
    )

    val F433 = Formation(
        "4-3-3",
        listOf(
            FormationSlot(Position.GK, Point(0.05f, 0.50f)),
            FormationSlot(Position.RB, Point(0.20f, 0.15f)),
            FormationSlot(Position.RCB, Point(0.18f, 0.38f)),
            FormationSlot(Position.LCB, Point(0.18f, 0.62f)),
            FormationSlot(Position.LB, Point(0.20f, 0.85f)),
            FormationSlot(Position.CDM, Point(0.38f, 0.50f)),
            FormationSlot(Position.RCM, Point(0.50f, 0.30f)),
            FormationSlot(Position.LCM, Point(0.50f, 0.70f)),
            FormationSlot(Position.RW, Point(0.78f, 0.15f)),
            FormationSlot(Position.LW, Point(0.78f, 0.85f)),
            FormationSlot(Position.CF, Point(0.82f, 0.50f))
        )
    )

    val F352 = Formation(
        "3-5-2",
        listOf(
            FormationSlot(Position.GK, Point(0.05f, 0.50f)),
            FormationSlot(Position.RCB, Point(0.18f, 0.30f)),
            FormationSlot(Position.CB, Point(0.16f, 0.50f)),
            FormationSlot(Position.LCB, Point(0.18f, 0.70f)),
            FormationSlot(Position.RWB, Point(0.45f, 0.10f)),
            FormationSlot(Position.CDM, Point(0.38f, 0.50f)),
            FormationSlot(Position.RCM, Point(0.48f, 0.35f)),
            FormationSlot(Position.LCM, Point(0.48f, 0.65f)),
            FormationSlot(Position.LWB, Point(0.45f, 0.90f)),
            FormationSlot(Position.RF, Point(0.78f, 0.40f)),
            FormationSlot(Position.LF, Point(0.78f, 0.60f))
        )
    )

    val ALL = listOf(F442, F433, F352)
}
