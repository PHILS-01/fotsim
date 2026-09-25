package com.tacticssim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.tacticssim.model.Kit
import com.tacticssim.model.Player
import com.tacticssim.model.Point
import com.tacticssim.model.Position
import com.tacticssim.model.RoleGroup

private val PITCH_GREEN = Color(0xFF2E7D32)
private val LINE_WHITE = Color(0xFFFFFFFF).copy(alpha = 0.85f)
private val BALL_LEATHER = Color(0xFFF5F5F5)

/**
 * Renders the pitch, both teams' players in their chosen kit colors (this is
 * what makes Team A and Team B visually distinct — previously both teams
 * shared the same role-based palette, which was the color-coding bug), and
 * the ball. Each marker shows its position abbreviation; the goalkeeper gets
 * a square marker instead of a circle so it reads at a glance regardless of
 * kit color choice.
 */
@Composable
fun PitchView(
    homePlayers: List<Player>,
    homeKit: Kit,
    awayPlayers: List<Player>,
    awayKit: Kit,
    ball: Point,
    modifier: Modifier = Modifier,
    homePlayersPrev: List<Player> = homePlayers,
    awayPlayersPrev: List<Player> = awayPlayers
) {
    val homePrevById = remember(homePlayersPrev) { homePlayersPrev.associateBy { it.id } }
    val awayPrevById = remember(awayPlayersPrev) { awayPlayersPrev.associateBy { it.id } }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawPitchLines()
        homePlayers.forEach { drawPlayerMarker(it, homeKit, homePrevById[it.id]?.current) }
        awayPlayers.forEach { drawPlayerMarker(it, awayKit, awayPrevById[it.id]?.current) }
        drawBall(ball)
    }
}

internal fun DrawScope.drawPitchLines() {
    drawRect(color = PITCH_GREEN)

    val w = size.width
    val h = size.height
    val strokeWidth = 2f * density

    drawRect(color = LINE_WHITE, topLeft = Offset(0f, 0f), size = Size(w, h), style = Stroke(strokeWidth))
    drawLine(LINE_WHITE, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth)
    drawCircle(LINE_WHITE, radius = h * 0.12f, center = Offset(w / 2, h / 2), style = Stroke(strokeWidth))

    val boxWidth = w * 0.16f
    val boxHeight = h * 0.55f
    drawRect(LINE_WHITE, topLeft = Offset(0f, (h - boxHeight) / 2), size = Size(boxWidth, boxHeight), style = Stroke(strokeWidth))
    drawRect(LINE_WHITE, topLeft = Offset(w - boxWidth, (h - boxHeight) / 2), size = Size(boxWidth, boxHeight), style = Stroke(strokeWidth))
}

private fun DrawScope.drawPlayerMarker(player: Player, kit: Kit, previous: Point?) {
    val cx = player.current.x * size.width
    val cy = player.current.y * size.height
    val shirt = Color(android.graphics.Color.parseColor(kit.shirtHex))
    val trim = Color(android.graphics.Color.parseColor(kit.trimHex))
    val radius = size.height * 0.024f

    // Soft ground shadow first, offset slightly, so markers read as standing
    // on the pitch rather than floating flat dots.
    drawCircle(
        color = Color.Black.copy(alpha = 0.22f),
        radius = radius * 0.9f,
        center = Offset(cx, cy + radius * 0.35f)
    )

    // A short motion streak trailing back from the player's last position:
    // faster movement (bigger step since the previous frame) stretches the
    // streak further, giving a sense of run/sprint rather than a robotic glide.
    if (previous != null) {
        val px = previous.x * size.width
        val py = previous.y * size.height
        val dx = cx - px
        val dy = cy - py
        val stepDist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (stepDist > 0.6f) {
            val trailLen = (stepDist * 1.6f).coerceAtMost(radius * 2.2f)
            val ux = dx / stepDist
            val uy = dy / stepDist
            drawLine(
                color = shirt.copy(alpha = 0.35f),
                start = Offset(cx - ux * trailLen, cy - uy * trailLen),
                end = Offset(cx - ux * radius * 0.6f, cy - uy * radius * 0.6f),
                strokeWidth = radius * 0.7f
            )
        }
    }

    if (player.position.group == RoleGroup.GOALKEEPER) {
        // Square marker so the keeper is identifiable regardless of kit color.
        drawRect(
            color = shirt,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2)
        )
        drawRect(
            color = trim,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(2f)
        )
    } else {
        drawCircle(color = shirt, radius = radius, center = Offset(cx, cy))
        drawCircle(color = trim, radius = radius, center = Offset(cx, cy), style = Stroke(2f))
    }

    // Position label so role is legible independent of jersey color.
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = trim.toArgb()
            textSize = radius * 1.1f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        drawText(player.position.label, cx, cy + radius * 0.35f, paint)
    }
}

private fun DrawScope.drawBall(ball: Point) {
    val cx = ball.x * size.width
    val cy = ball.y * size.height
    val r = size.height * 0.014f

    // Simple ball: white body with a dark pentagon-ish center dot and seam lines,
    // reads as "a ball" rather than a plain dot at this scale.
    drawCircle(color = BALL_LEATHER, radius = r, center = Offset(cx, cy))
    drawCircle(color = Color.Black.copy(alpha = 0.8f), radius = r, center = Offset(cx, cy), style = Stroke(1.2f))
    drawCircle(color = Color.Black.copy(alpha = 0.7f), radius = r * 0.35f, center = Offset(cx, cy))
}
