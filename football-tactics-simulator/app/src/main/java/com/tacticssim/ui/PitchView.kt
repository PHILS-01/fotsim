package com.tacticssim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.tacticssim.model.Player
import com.tacticssim.model.Point
import com.tacticssim.model.Position

private val PITCH_GREEN = Color(0xFF2E7D32)
private val LINE_WHITE = Color(0xFFFFFFFF).copy(alpha = 0.85f)
private val BALL_COLOR = Color(0xFFFFFFFF)

@Composable
fun PitchView(
    homePlayers: List<Player>,
    awayPlayers: List<Player>,
    ball: Point,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawPitchLines()
        (homePlayers + awayPlayers).forEach { player ->
            drawPlayerDot(player)
        }
        drawBall(ball)
    }
}

private fun DrawScope.drawPitchLines() {
    drawRect(color = PITCH_GREEN)

    val w = size.width
    val h = size.height
    val strokeWidth = 2f * density

    // Outer boundary
    drawRect(color = LINE_WHITE, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
        size = androidx.compose.ui.geometry.Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))

    // Halfway line
    drawLine(LINE_WHITE, androidx.compose.ui.geometry.Offset(w / 2, 0f), androidx.compose.ui.geometry.Offset(w / 2, h), strokeWidth)

    // Center circle
    drawCircle(LINE_WHITE, radius = h * 0.12f, center = androidx.compose.ui.geometry.Offset(w / 2, h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))

    // Penalty boxes (left and right)
    val boxWidth = w * 0.16f
    val boxHeight = h * 0.55f
    drawRect(LINE_WHITE, topLeft = androidx.compose.ui.geometry.Offset(0f, (h - boxHeight) / 2),
        size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight), style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))
    drawRect(LINE_WHITE, topLeft = androidx.compose.ui.geometry.Offset(w - boxWidth, (h - boxHeight) / 2),
        size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight), style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))
}

private fun DrawScope.drawPlayerDot(player: Player) {
    val cx = player.current.x * size.width
    val cy = player.current.y * size.height
    val colorHex = Position.colorHexFor(player.position.group)
    val color = Color(android.graphics.Color.parseColor(colorHex))
    val radius = size.height * 0.022f

    drawCircle(color = color, radius = radius, center = androidx.compose.ui.geometry.Offset(cx, cy))
    drawCircle(
        color = Color.Black.copy(alpha = 0.6f),
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)
    )
}

private fun DrawScope.drawBall(ball: Point) {
    val cx = ball.x * size.width
    val cy = ball.y * size.height
    drawCircle(color = BALL_COLOR, radius = size.height * 0.012f, center = androidx.compose.ui.geometry.Offset(cx, cy))
}

