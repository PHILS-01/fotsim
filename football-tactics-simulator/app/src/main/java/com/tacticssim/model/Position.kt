package com.tacticssim.model

/**
 * Every playable position. Grouped into RoleGroup for color coding and
 * for the movement engine's zone-of-responsibility logic.
 */
enum class RoleGroup { GOALKEEPER, DEFENSE, MIDFIELD, ATTACK }

enum class Position(val label: String, val group: RoleGroup) {
    GK("GK", RoleGroup.GOALKEEPER),

    RB("RB", RoleGroup.DEFENSE),
    LB("LB", RoleGroup.DEFENSE),
    RCB("RCB", RoleGroup.DEFENSE),
    LCB("LCB", RoleGroup.DEFENSE),
    CB("CB", RoleGroup.DEFENSE),
    RWB("RWB", RoleGroup.DEFENSE),
    LWB("LWB", RoleGroup.DEFENSE),

    CDM("CDM", RoleGroup.MIDFIELD),
    CM("CM", RoleGroup.MIDFIELD),
    RCM("RCM", RoleGroup.MIDFIELD),
    LCM("LCM", RoleGroup.MIDFIELD),
    CAM("CAM", RoleGroup.MIDFIELD),
    RM("RM", RoleGroup.MIDFIELD),
    LM("LM", RoleGroup.MIDFIELD),

    RW("RW", RoleGroup.ATTACK),
    LW("LW", RoleGroup.ATTACK),
    RF("RF", RoleGroup.ATTACK),
    LF("LF", RoleGroup.ATTACK),
    CF("CF", RoleGroup.ATTACK),
    ST("ST", RoleGroup.ATTACK);

    companion object {
        /** Hex color per role group, used for the pitch markers and heat maps. */
        fun colorHexFor(group: RoleGroup): String = when (group) {
            RoleGroup.GOALKEEPER -> "#FBC02D" // yellow
            RoleGroup.DEFENSE -> "#1E88E5"    // blue
            RoleGroup.MIDFIELD -> "#43A047"   // green
            RoleGroup.ATTACK -> "#E53935"     // red
        }
    }
}
