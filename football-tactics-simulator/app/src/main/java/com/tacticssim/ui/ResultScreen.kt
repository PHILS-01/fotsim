package com.tacticssim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tacticssim.engine.ProgressStore

@Composable
fun ResultScreen(result: MatchResult, onPlayAgain: () -> Unit) {
    val context = LocalContext.current
    val totalCredits = remember { ProgressStore.credits(context) }

    val outcome = when {
        result.homeGoals > result.awayGoals -> "VICTORY"
        result.homeGoals == result.awayGoals -> "DRAW"
        else -> "DEFEAT"
    }
    val outcomeColor = when (outcome) {
        "VICTORY" -> Color(0xFF4CAF50)
        "DRAW" -> Color(0xFFAAAAAA)
        else -> Color(0xFFE05050)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101418)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Full Time", color = Color(0xFF8A8A8A), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(outcome, color = outcomeColor, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "${result.homeName} ${result.homeGoals} - ${result.awayGoals} ${result.awayName}",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(24.dp))
        Text("+${result.creditsEarned} credits earned", color = Color(0xFFFFD54F), style = MaterialTheme.typography.titleMedium)
        Text("Total credits: $totalCredits", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodyMedium)
        Text(
            "Unlock a bonus kit every ${ProgressStore.UNLOCK_THRESHOLD_PER_KIT} credits.",
            color = Color(0xFF6A6A6A),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPlayAgain) { Text("Back to Setup") }
    }
}
