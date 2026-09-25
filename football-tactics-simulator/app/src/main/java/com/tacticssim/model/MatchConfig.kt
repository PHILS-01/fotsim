package com.tacticssim.model

data class TeamConfig(
    val name: String,
    val kit: Kit,
    val formation: Formation,
    val strategy: Strategy,
    val controller: Controller
)

data class MatchConfig(
    val home: TeamConfig,
    val away: TeamConfig,
    val halfLengthRealSeconds: Int,
    val commentaryLanguage: CommentaryLanguage = CommentaryLanguage.DEFAULT
)
