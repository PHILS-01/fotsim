package com.tacticssim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tacticssim.model.MatchConfig
import com.tacticssim.ui.MatchResult
import com.tacticssim.ui.MatchScreen
import com.tacticssim.ui.ResultScreen
import com.tacticssim.ui.SetupScreen

private sealed class Screen {
    object Setup : Screen()
    data class Match(val config: MatchConfig) : Screen()
    data class Result(val result: MatchResult) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf<Screen>(Screen.Setup) }

                    when (val s = screen) {
                        is Screen.Setup -> SetupScreen(onStart = { config -> screen = Screen.Match(config) })
                        is Screen.Match -> MatchScreen(config = s.config, onMatchOver = { result -> screen = Screen.Result(result) })
                        is Screen.Result -> ResultScreen(result = s.result, onPlayAgain = { screen = Screen.Setup })
                    }
                }
            }
        }
    }
}
