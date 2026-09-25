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
import com.tacticssim.engine.*
import com.tacticssim.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Selectable half lengths. Real time in the app maps to a 45-min half at this pace,
 * so a "5 min" setting compresses each simulated minute into (5*60)/45 real seconds. */
enum class HalfLength(val label: String, val realSeconds: Int) {
    FIVE("5 min", 5 * 60),
    TEN("10 min", 10 * 60),
    FIFTEEN("15 min", 15 * 60),
    TWENTY("20 min", 20 * 60)
}

@Composable
fun MatchScreen() {
    val context = LocalContext.current
    val stadiumSound = remember { StadiumSound(context) }

    var halfLength by remember { mutableStateOf(HalfLength.TEN) }
    var isPlaying by remember { mutableStateOf(false) }
    var isReplaying by remember { mutableStateOf(false) }
    var replayIndex by remember { mutableStateOf(0) }

    val homeFormation = Formations.F433
    val awayFormation = Formations.F442

    val engine = remember(halfLength) {
        val home = buildTeam(Side.HOME, "Home", homeFormation)
        val away = buildTeam(Side.AWAY, "Away", awayFormation)
        MatchEngine(home, away, homeFormation, awayFormation)
    }
    val commentaryGen = remember(engine) { CommentaryGenerator("Home", "Away") }
    val commentaryLines = remember(engine) { mutableStateListOf<CommentaryLine>() }

    // Live render state (either live tick or scrubbed replay frame)
    var homePositions by remember { mutableStateOf(engine.frames.lastOrNull()?.homePositions ?: emptyMap()) }
    var awayPositions by remember { mutableStateOf(engine.frames.lastOrNull()?.awayPositions ?: emptyMap()) }
    var ballPos by remember { mutableStateOf(Point(0.5f, 0.5f)) }
    var secondHalfStarted by remember { mutableStateOf(false) }

    // Real-seconds-per-simulated-minute pacing derived from chosen half length.
    val tickDelayMs = remember(halfLength) { (halfLength.realSeconds * 1000L) / (45 * 60) }

    fun syncFromLatestFrame() {
        val f = engine.frames.lastOrNull() ?: return
        homePositions = f.homePositions
        awayPositions = f.awayPositions
        ballPos = f.ball
    }

    fun pushCommentary(ev: MatchEvent?) {
        ev?.let { commentaryGen.forEvent(it)?.let { line -> commentaryLines.add(0, line) } }
    }

    // Kickoff once, on first composition.
    LaunchedEffect(engine) {
        val ev = engine.kickoff()
        syncFromLatestFrame()
        pushCommentary(ev)
        stadiumSound.startAmbience()
    }

    // Live simulation loop.
    LaunchedEffect(isPlaying, engine, tickDelayMs) {
        while (isActive && isPlaying) {
            delay(tickDelayMs.coerceAtLeast(16L))
            val ev = engine.step()
            syncFromLatestFrame()
            pushCommentary(ev)

            if (!secondHalfStarted && engine.currentMinute() >= 45) {
                secondHalfStarted = true
                isPlaying = false
                stadiumSound.playWhistle()
                val htEv = engine.startSecondHalf()
                syncFromLatestFrame()
                pushCommentary(htEv)
            }
            if (engine.currentMinute() >= 90) {
                isPlaying = false
                stadiumSound.playWhistle()
                pushCommentary(MatchEvent(EventType.FULLTIME, engine.currentMinute(), engine.currentSecond(), Side.HOME))
            }
        }
    }

    // Replay scrub loop: steps through stored frames without re-simulating.
    LaunchedEffect(isReplaying, replayIndex) {
        if (isReplaying && replayIndex < engine.frames.size) {
            val f = engine.frames[replayIndex]
            homePositions = f.homePositions
            awayPositions = f.awayPositions
            ballPos = f.ball
        }
    }

    DisposableEffect(Unit) {
        onDispose { stadiumSound.stopAmbience() }
    }

    val homePlayersRendered = engine.homeTeamSnapshot(homePositions)
    val awayPlayersRendered = engine.awayTeamSnapshot(awayPositions)
    val (homePoss, awayPoss) = engine.stats.possessionPercent()

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF101418))) {
        // Left: pitch + controls
        Column(modifier = Modifier.weight(2f).padding(8.dp)) {
            Scoreboard(
                homeGoals = engine.stats.homeGoals,
                awayGoals = engine.stats.awayGoals,
                minute = engine.currentMinute(),
                second = engine.currentSecond()
            )
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                PitchView(homePlayersRendered, awayPlayersRendered, ballPos, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(6.dp))
            ControlsRow(
                halfLength = halfLength,
                onHalfLengthChange = { halfLength = it },
                isPlaying = isPlaying,
                onPlayPause = {
                    isReplaying = false
                    isPlaying = !isPlaying
                },
                onReplayToggle = {
                    isPlaying = false
                    isReplaying = !isReplaying
                    if (isReplaying) replayIndex = (engine.frames.size - 300).coerceAtLeast(0)
                },
                isReplaying = isReplaying,
                replayIndex = replayIndex,
                maxFrame = (engine.frames.size - 1).coerceAtLeast(0),
                onScrub = { replayIndex = it }
            )
        }

        // Right: stats + commentary
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
    halfLength: HalfLength,
    onHalfLengthChange: (HalfLength) -> Unit,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    isReplaying: Boolean,
    onReplayToggle: () -> Unit,
    replayIndex: Int,
    maxFrame: Int,
    onScrub: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HalfLength.entries.forEach { hl ->
                FilterChip(
                    selected = halfLength == hl,
                    onClick = { onHalfLengthChange(hl) },
                    label = { Text(hl.label) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlayPause) { Text(if (isPlaying) "Pause" else "Play") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onReplayToggle) { Text(if (isReplaying) "Exit Replay" else "Replay Last Passage") }
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
