package com.tacticssim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tacticssim.model.Formation
import com.tacticssim.model.FormationSlot
import com.tacticssim.model.Kit
import com.tacticssim.model.Point
import com.tacticssim.model.Position
import com.tacticssim.model.RoleGroup

/** One draggable/reassignable marker in the editor. Plain observable-fields
 * class (not a data class) so each marker keeps stable identity across
 * recomposition while its x/y/position mutate independently. */
private class EditableSlot(position: Position, x: Float, y: Float) {
    var position by mutableStateOf(position)
    var x by mutableStateOf(x)
    var y by mutableStateOf(y)
}

/**
 * Full-screen formation editor. Unlike the fixed presets, this allows any
 * arrangement at all: any of the 11 markers can be dragged anywhere on the
 * pitch, and reassigned to any position label independently (so you can
 * build a back 3, a diamond midfield, a lone striker, or anything else).
 * The count stays at 11 (a real side), but placement and roles are fully free.
 */
@Composable
fun FormationEditorScreen(
    initial: Formation,
    previewKit: Kit,
    onSave: (Formation) -> Unit,
    onCancel: () -> Unit
) {
    val slots = remember(initial) {
        initial.slots.map { EditableSlot(it.position, it.zone.x, it.zone.y) }
    }
    var pitchSizePx by remember { mutableStateOf(IntSize(1, 1)) }
    var dropdownForIndex by remember { mutableStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101418)).padding(12.dp)) {
        Text("Custom Formation", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(
            "Drag any player anywhere on the pitch. Tap a row below to change its role.",
            color = Color(0xFF8A8A8A),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Any split is allowed — 4-3-3, 8-1-1, whatever you want, as long as the outfield count is 10.",
            color = Color(0xFF6A6A6A),
            style = MaterialTheme.typography.bodySmall
        )

        // Live counts so an arrangement like "8-1-1" is easy to see and confirm as you build it.
        val gkCount = slots.count { it.position.group == RoleGroup.GOALKEEPER }
        val defCount = slots.count { it.position.group == RoleGroup.DEFENSE }
        val midCount = slots.count { it.position.group == RoleGroup.MIDFIELD }
        val attCount = slots.count { it.position.group == RoleGroup.ATTACK }
        val outfieldTotal = defCount + midCount + attCount

        Spacer(Modifier.height(6.dp))
        Text(
            "GK $gkCount  ·  DEF $defCount  ·  MID $midCount  ·  ATT $attCount   (outfield total: $outfieldTotal)",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        if (gkCount != 1) {
            Text(
                "Heads up: a real side has exactly 1 goalkeeper — you currently have $gkCount. " +
                    "This will still save if you want it this way.",
                color = Color(0xFFE0A030),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { pitchSizePx = it }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawPitchLines() }

            slots.forEach { slot ->
                val cx = slot.x * pitchSizePx.width
                val cy = slot.y * pitchSizePx.height
                val radiusPx = pitchSizePx.height * 0.028f

                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((cx - radiusPx).toInt(), (cy - radiusPx).toInt()) }
                        .size(with(androidx.compose.ui.platform.LocalDensity.current) { (radiusPx * 2).toDp() })
                        .pointerInput(slot) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val w = pitchSizePx.width.coerceAtLeast(1)
                                val h = pitchSizePx.height.coerceAtLeast(1)
                                slot.x = (slot.x + dragAmount.x / w).coerceIn(0.02f, 0.98f)
                                slot.y = (slot.y + dragAmount.y / h).coerceIn(0.03f, 0.97f)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val minDim = kotlin.math.min(size.width, size.height)
                        val shirt = Color(android.graphics.Color.parseColor(previewKit.shirtHex))
                        val trim = Color(android.graphics.Color.parseColor(previewKit.trimHex))
                        drawCircle(color = shirt, radius = minDim / 2f, center = Offset(size.width / 2, size.height / 2))
                        drawCircle(
                            color = trim,
                            radius = minDim / 2f,
                            center = Offset(size.width / 2, size.height / 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2f)
                        )
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = trim.toArgb()
                                textSize = minDim * 0.42f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                isFakeBoldText = true
                            }
                            drawText(slot.position.label, size.width / 2, size.height / 2 + minDim * 0.15f, paint)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Roles (tap to reassign)", color = Color(0xFF8A8A8A), style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.height(140.dp)) {
            items(slots.size) { index ->
                val slot = slots[index]
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Player ${index + 1}", color = Color.White)
                        TextButton(onClick = { dropdownForIndex = index }) {
                            Text(slot.position.label)
                        }
                    }
                    DropdownMenu(expanded = dropdownForIndex == index, onDismissRequest = { dropdownForIndex = -1 }) {
                        Position.entries.forEach { pos ->
                            DropdownMenuItem(
                                text = { Text(pos.label) },
                                onClick = {
                                    slot.position = pos
                                    dropdownForIndex = -1
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val formation = Formation("Custom", slots.map { FormationSlot(it.position, Point(it.x, it.y)) })
                    onSave(formation)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save Formation") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}
