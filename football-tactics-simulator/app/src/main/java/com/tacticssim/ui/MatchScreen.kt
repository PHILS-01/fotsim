package com.tacticssim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.tacticssim.audio.StadiumSound
import com.tacticssim.audio.CommentaryVoice
import com.tacticssim.engine.*
import com.tacticssim.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

data class MatchResult(val homeName: String, val awayName: String, val homeGoals: Int, val awayGoals: Int, val creditsEarned: Int)

@Composable
fun MatchScreen(config: MatchConfig, onMatchOver: (MatchResult) -> Unit) {
    val context = LocalContext.current
    val stadiumSound = remember { StadiumSound(context) }
    var voiceEnabled by remember { mutableStateOf(true) }
    val commentaryVoice = remember {
        CommentaryVoice(context, config.commentaryLanguage, onSpeakingChanged = { speaking -> stadiumSound.setDucked(speaking) })
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isReplaying by remember { mutableStateOf(false) }
    var replayIndex by remember { mutableStateOf(0) }
    var varOverlayVisible by remember { mutableStateOf(false) }
    var varResultText by remember { mutableStateOf<String?>(null) }
    var secondHalfStarted by remember { mutableStateOf(false) }
    var matchEnded by remember { mutableStateOf(false) }

    val engine = remember(config) {
        val home = buildTeam(Side.HOME, config.home.name, config.home.formation, config.home.kit, config.home.strategy, config.home.controller)
        val away = buildTeam(Side.AWAY, config.away.name, config.away.formation, config.away.kit, config.away.strategy, config.away.controller)
        MatchEngine(home, away)
    }
    val commentaryGen = remember(engine) { CommentaryGenerator(config.home.name, config.away.name, config.commentaryLanguage) }
    val commentaryLines = remember(engine) { mutableStateListOf<CommentaryLine>() }
    val varRng = remember { Random(System.currentTimeMillis()) }

    var homePositions by remember { mutableStateOf(engine.frames.lastOrNull()?.homePositions ?: emptyMap()) }
    var awayPositions by remember { mutableStateOf(engine.frames.lastOrNull()?.awayPositions ?: emptyMap()) }
    var prevHomePositions by remember { mutableStateOf(homePositions) }
    var prevAwayPositions by remember { mutableStateOf(awayPositions) }
    var ballPos by remember { mutableStateOf(Point(0.5f, 0.5f)) }

    val tickDelayMs = remember(config) { (config.halfLengthRealSeconds * 1000L) / (45 * 60) }

    fun syncFromLatestFrame() {
        val f = engine.frames.lastOrNull() ?: return
        prevHomePositions = homePositions
        prevAwayPositions = awayPositions
        homePositions = f.homePositions
        awayPositions = f.awayPositions
        ballPos = f.ball
    }

    fun pushCommentary(ev: MatchEvent?) {
        ev?.let {
            commentaryGen.forEvent(it)?.let { line ->
                commentaryLines.add(0, line)
                commentaryVoice.speak(line.text)
            }
        }
    }

    fun endMatch() {
        matchEnded = true
        isPlaying = false
        stadiumSound.playWhistle()
        pushCommentary(MatchEvent(EventType.FULLTIME, engine.currentMinute(), engine.currentSecond(), Side.HOME))
        val earned = ProgressStore.recordResult(context, engine.stats.homeGoals, engine.stats.awayGoals)
        onMatchOver(MatchResult(config.home.name, config.away.name, engine.stats.homeGoals, engine.stats.awayGoals, earned))
    }

    // Kickoff once, on first composition.
    LaunchedEffect(engine) {
        stadiumSound.startAmbience()
        stadiumSound.playWhistle()
        val ev = engine.kickoff()
        syncFromLatestFrame()
        pushCommentary(ev)
    }

    // Live simulation loop.
    LaunchedEffect(isPlaying, engine, tickDelayMs) {
        while (isActive && isPlaying) {
            delay(tickDelayMs.coerceAtLeast(16L))
            val ev = engine.step()
            syncFromLatestFrame()

            if (ev?.type == EventType.VAR_REVIEW) {
                isPlaying = false
                varOverlayVisible = true
                pushCommentary(ev)
            } else {
                pushCommentary(ev)
            }

            if (!secondHalfStarted && engine.currentMinute() >= 45 && !varOverlayVisible) {
                secondHalfStarted = true
                isPlaying = false
                stadiumSound.playWhistle()
                val htEv = engine.startSecondHalf()
                syncFromLatestFrame()
                pushCommentary(htEv)
            }
            if (engine.currentMinute() >= 90 && !varOverlayVisible) {
                endMatch()
            }
        }
    }

    // VAR review resolution: pause for a beat, then auto-resolve (mostly confirmed,
    // occasionally overturned, like a real review) and resume.
    LaunchedEffect(varOverlayVisible) {
        if (varOverlayVisible) {
            delay(2200L)
            val confirmed = varRng.nextFloat() < 0.82f
            val resolved = engine.resolveVar(confirmed)
            syncFromLatestFrame()
            varResultText = if (confirmed) "GOAL CONFIRMED" else "NO GOAL — OVERTURNED"
            pushCommentary(resolved)
            delay(1400L)
            varOverlayVisible = false
            varResultText = null
            if (engine.currentMinute() >= 90) {
                endMatch()
            } else {
                isPlaying = true
            }
        }
    }

    LaunchedEffect(isReplaying, replayIndex) {
        if (isReplaying && replayIndex < engine.frames.size) {
            val f = engine.frames[replayIndex]
            homePositions = f.homePositions
            awayPositions = f.awayPositions
            ballPos = f.ball
        }
    }

    LaunchedEffect(voiceEnabled) {
        commentaryVoice.enabled = voiceEnabled
        if (!voiceEnabled) {
            commentaryVoice.stop()
            stadiumSound.setDucked(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stadiumSound.stopAmbience()
            commentaryVoice.shutdown()
        }
    }

    val homePlayersRendered = engine.homeTeamSnapshot(homePositions)
    val awayPlayersRendered = engine.awayTeamSnapshot(awayPositions)
    val homePlayersPrev = engine.homeTeamSnapshot(prevHomePositions)
    val awayPlayersPrev = engine.awayTeamSnapshot(prevAwayPositions)
    val (homePoss, awayPoss) = engine.stats.possessionPercent()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(Color(0xFF101418))) {
            Column(modifier = Modifier.weight(2f).padding(8.dp)) {
                Scoreboard(
                    homeGoals = engine.stats.homeGoals,
                    awayGoals = engine.stats.awayGoals,
                    minute = engine.currentMinute(),
                    second = engine.currentSecond()
                )
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    PitchView(
                        homePlayersRendered, config.home.kit,
                        awayPlayersRendered, config.away.kit,
                        ballPos, modifier = Modifier.fillMaxSize(),
                        homePlayersPrev = homePlayersPrev,
                        awayPlayersPrev = awayPlayersPrev
                    )
                }
                Spacer(Modifier.height(6.dp))
                ControlsRow(
                    isPlaying = isPlaying,
                    matchEnded = matchEnded,
                    onPlayPause = {
                        if (!varOverlayVisible && !matchEnded) {
                            isReplaying = false
                            isPlaying = !isPlaying
                        }
                    },
                    onReplayToggle = {
                        if (!varOverlayVisible) {
                            isPlaying = false
                            isReplaying = !isReplaying
                            if (isReplaying) replayIndex = (engine.frames.size - 300).coerceAtLeast(0)
                        }
                    },
                    isReplaying = isReplaying,
                    replayIndex = replayIndex,
                    maxFrame = (engine.frames.size - 1).coerceAtLeast(0),
                    onScrub = { replayIndex = it },
                    voiceEnabled = voiceEnabled,
                    onToggleVoice = { voiceEnabled = !voiceEnabled }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF181D23))
                    .padding(12.dp)
            ) {
                Text("Match Stats", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                StatRow("Possession", "$homePoss%", "$awayPoss%")
                StatRow("xG", String.format("%.2f", engine.stats.homeXg), String.format("%.2f", engine.stats.awayXg))
                StatRow("Passes", "${engine.stats.homePasses}", "${engine.stats.awayPasses}")
                StatRow("Chances", "${engine.stats.homeChances}", "${engine.stats.awayChances}")
                StatRow("Corners", "${engine.stats.homeCorners}", "${engine.stats.awayCorners}")
                StatRow("Free Kicks", "${engine.stats.homeFreeKicks}", "${engine.stats.awayFreeKicks}")

                Spacer(Modifier.height(16.dp))
                Text("Commentary", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(commentaryLines) { line ->
                        Text(
                            "${line.minute}' ${line.text}",
                            color = Color(0xFFD0D0D0),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (varOverlayVisible) {
            VarOverlay(resultText = varResultText)
        }
    }
}

@Composable
private fun VarOverlay(resultText: String?) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VAR REVIEW", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            if (resultText == null) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Checking for offside / foul in the build-up...", color = Color(0xFFCCCCCC))
            } else {
                Text(
                    resultText,
                    color = if (resultText.startsWith("GOAL")) Color(0xFF4CAF50) else Color(0xFFE05050),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun Scoreboard(homeGoals: Int, awayGoals: Int, minute: Int, second: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1E242B)).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("HOME  $homeGoals - $awayGoals  AWAY", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(String.format("%02d:%02d", minute, second), color = Color(0xFFAAAAAA), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StatRow(label: String, homeVal: String, awayVal: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(homeVal, color = Color.White)
        Text(label, color = Color(0xFF8A8A8A), style = MaterialTheme.typography.bodySmall)
        Text(awayVal, color = Color.White)
    }
}

@Composable
private fun ControlsRow(
    isPlaying: Boolean,
    matchEnded: Boolean,
    onPlayPause: () -> Unit,
    isReplaying: Boolean,
    onReplayToggle: () -> Unit,
    replayIndex: Int,
    maxFrame: Int,
    onScrub: (Int) -> Unit,
    voiceEnabled: Boolean,
    onToggleVoice: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlayPause, enabled = !matchEnded) { Text(if (isPlaying) "Pause" else "Play") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onReplayToggle) { Text(if (isReplaying) "Exit Replay" else "Replay Last Passage") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onToggleVoice) { Text(if (voiceEnabled) "Commentary: On" else "Commentary: Off") }
        }
        if (isReplaying && maxFrame > 0) {
            Spacer(Modifier.height(6.dp))
            Slider(
                value = replayIndex.toFloat(),
                onValueChange = { onScrub(it.toInt()) },
                valueRange = 0f..maxFrame.toFloat()
            )
        }
    }
}
