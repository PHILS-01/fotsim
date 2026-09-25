package com.tacticssim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tacticssim.engine.ProgressStore
import com.tacticssim.engine.SettingsStore
import com.tacticssim.model.*

@Composable
fun SetupScreen(onStart: (MatchConfig) -> Unit) {
    val context = LocalContext.current
    val credits = remember { ProgressStore.credits(context) }
    val unlockedLabels = remember { ProgressStore.unlockedKitLabels(context) }
    val availableKits = remember { Kit.PRESETS + Kit.UNLOCKABLE.filter { it.label in unlockedLabels } }

    var homeName by remember { mutableStateOf("Home") }
    var awayName by remember { mutableStateOf("Away") }
    var homeKit by remember { mutableStateOf(availableKits[0]) }
    var awayKit by remember { mutableStateOf(availableKits[1]) }
    var homeFormation by remember { mutableStateOf(Formations.F433) }
    var awayFormation by remember { mutableStateOf(Formations.F442) }
    var homeCustomFormation by remember { mutableStateOf<Formation?>(null) }
    var awayCustomFormation by remember { mutableStateOf<Formation?>(null) }
    var homeStrategy by remember { mutableStateOf(Strategy.POSSESSION) }
    var awayStrategy by remember { mutableStateOf(Strategy.COUNTER_ATTACK) }
    var opponentMode by remember { mutableStateOf(OpponentMode.AI_MEDIUM) }
    var halfLength by remember { mutableStateOf(HalfLength.TEN) }
    var commentaryLanguage by remember { mutableStateOf(SettingsStore.commentaryLanguage(context)) }
    var formationEditorTarget by remember { mutableStateOf<Side?>(null) }

    val kitClash = homeKit.shirtHex == awayKit.shirtHex

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101418))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Football Tactics Simulator", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Credits: $credits", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            TeamSetupBlock(
                title = "Your Team",
                name = homeName, onNameChange = { homeName = it },
                kit = homeKit, availableKits = availableKits, onKitChange = { homeKit = it },
                formation = homeFormation, onFormationChange = { homeFormation = it },
                customFormation = homeCustomFormation,
                onOpenFormationEditor = { formationEditorTarget = Side.HOME },
                strategy = homeStrategy, onStrategyChange = { homeStrategy = it }
            )

            Spacer(Modifier.height(20.dp))
            Text("Opponent", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Row {
                OpponentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = opponentMode == mode,
                        onClick = { opponentMode = mode },
                        label = { Text(mode.label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            if (opponentMode == OpponentMode.ONLINE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Online play isn't available yet — it needs a matchmaking server. Coming in a future update.",
                    color = Color(0xFFE0A030),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(12.dp))
            TeamSetupBlock(
                title = if (opponentMode == OpponentMode.LOCAL_PASS_AND_PLAY) "Second Player" else "Computer Team",
                name = awayName, onNameChange = { awayName = it },
                kit = awayKit, availableKits = availableKits, onKitChange = { awayKit = it },
                formation = awayFormation, onFormationChange = { awayFormation = it },
                customFormation = awayCustomFormation,
                onOpenFormationEditor = { formationEditorTarget = Side.AWAY },
                strategy = awayStrategy, onStrategyChange = { awayStrategy = it }
            )

            if (kitClash) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Both teams have the same shirt color — pick a different kit for one side.",
                    color = Color(0xFFE05050),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Half Length", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Row {
                HalfLength.entries.forEach { hl ->
                    FilterChip(
                        selected = halfLength == hl,
                        onClick = { halfLength = hl },
                        label = { Text(hl.label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("Commentary Language", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            LazyRow {
                items(CommentaryLanguage.entries) { lang ->
                    FilterChip(
                        selected = commentaryLanguage == lang,
                        onClick = {
                            commentaryLanguage = lang
                            SettingsStore.setCommentaryLanguage(context, lang)
                        },
                        label = { Text(lang.label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val awayController = when (opponentMode) {
                        OpponentMode.AI_MEDIUM -> Controller.Ai(AiDifficulty.MEDIUM)
                        OpponentMode.AI_HARD -> Controller.Ai(AiDifficulty.HARD)
                        OpponentMode.LOCAL_PASS_AND_PLAY -> Controller.Human
                        OpponentMode.ONLINE -> Controller.Ai(AiDifficulty.MEDIUM) // stand-in until online exists
                    }
                    onStart(
                        MatchConfig(
                            home = TeamConfig(homeName, homeKit, homeFormation, homeStrategy, Controller.Human),
                            away = TeamConfig(awayName, awayKit, awayFormation, awayStrategy, awayController),
                            halfLengthRealSeconds = halfLength.realSeconds,
                            commentaryLanguage = commentaryLanguage
                        )
                    )
                },
                enabled = !kitClash,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kick Off")
            }
            Spacer(Modifier.height(24.dp))
        }

        val editingSide = formationEditorTarget
        if (editingSide != null) {
            val startingFormation = when (editingSide) {
                Side.HOME -> homeCustomFormation ?: homeFormation
                Side.AWAY -> awayCustomFormation ?: awayFormation
            }
            val previewKit = if (editingSide == Side.HOME) homeKit else awayKit
            FormationEditorScreen(
                initial = startingFormation,
                previewKit = previewKit,
                onSave = { custom ->
                    if (editingSide == Side.HOME) {
                        homeCustomFormation = custom
                        homeFormation = custom
                    } else {
                        awayCustomFormation = custom
                        awayFormation = custom
                    }
                    formationEditorTarget = null
                },
                onCancel = { formationEditorTarget = null }
            )
        }
    }
}

private enum class OpponentMode(val label: String) {
    AI_MEDIUM("Computer: Medium"),
    AI_HARD("Computer: Hard"),
    LOCAL_PASS_AND_PLAY("Pass & Play (this device)"),
    ONLINE("Online (coming soon)")
}

enum class HalfLength(val label: String, val realSeconds: Int) {
    FIVE("5 min", 5 * 60),
    TEN("10 min", 10 * 60),
    FIFTEEN("15 min", 15 * 60),
    TWENTY("20 min", 20 * 60)
}

@Composable
private fun TeamSetupBlock(
    title: String,
    name: String, onNameChange: (String) -> Unit,
    kit: Kit, availableKits: List<Kit>, onKitChange: (Kit) -> Unit,
    formation: Formation, onFormationChange: (Formation) -> Unit,
    customFormation: Formation?, onOpenFormationEditor: () -> Unit,
    strategy: Strategy, onStrategyChange: (Strategy) -> Unit
) {
    Column(modifier = Modifier.background(Color(0xFF181D23)).padding(12.dp).fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Team name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text("Jersey", color = Color(0xFF8A8A8A), style = MaterialTheme.typography.bodySmall)
        LazyRow {
            items(availableKits) { k ->
                val selected = k == kit
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp, top = 4.dp)
                        .size(36.dp)
                        .background(Color(android.graphics.Color.parseColor(k.shirtHex))),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Text(text = "✓", color = Color(android.graphics.Color.parseColor(k.trimHex)))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            availableKits.forEach { k ->
                TextButton(onClick = { onKitChange(k) }) {
                    Text(k.label, color = if (k == kit) Color.White else Color(0xFF8A8A8A))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Formation", color = Color(0xFF8A8A8A), style = MaterialTheme.typography.bodySmall)
        Row {
            Formations.ALL.forEach { f ->
                FilterChip(
                    selected = formation.name == f.name && formation === f,
                    onClick = { onFormationChange(f) },
                    label = { Text(f.name) },
                    modifier = Modifier.padding(end = 6.dp, top = 4.dp)
                )
            }
            if (customFormation != null) {
                FilterChip(
                    selected = formation.name == "Custom",
                    onClick = { onFormationChange(customFormation) },
                    label = { Text("Custom") },
                    modifier = Modifier.padding(end = 6.dp, top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOpenFormationEditor) {
            Text(if (customFormation != null) "Edit Custom Formation" else "Build Custom Formation...")
        }

        Spacer(Modifier.height(10.dp))
        Text("Strategy", color = Color(0xFF8A8A8A), style = MaterialTheme.typography.bodySmall)
        Row {
            Strategy.entries.forEach { s ->
                FilterChip(
                    selected = strategy == s,
                    onClick = { onStrategyChange(s) },
                    label = { Text(s.label) },
                    modifier = Modifier.padding(end = 6.dp, top = 4.dp)
                )
            }
        }
    }
}
