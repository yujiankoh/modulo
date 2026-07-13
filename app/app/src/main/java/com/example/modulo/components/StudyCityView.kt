package com.example.modulo.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.modulo.City
import com.example.modulo.helpers.CityLogicHelper

private val BUILDING_HUES = listOf(
    Color(0xFFE8B04A), Color(0xFF6F9FE8), Color(0xFF8FC9CF), Color(0xFFD98A7A), Color(0xFFB3A1E0)
)

private fun Color.shade(f: Float) = copy(
    red = (red * f).coerceIn(0f, 1f),
    green = (green * f).coerceIn(0f, 1f),
    blue = (blue * f).coerceIn(0f, 1f)
)

@Composable
fun StudyCityView(city: City, totalMins: Int, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val grass = if (dark) Color(0xFF2E4A39) else Color(0xFF9ECF90)
    val grassEdge = if (dark) Color(0xFF243A2C) else Color(0xFF86BE7A)

    Box(modifier, contentAlignment = Alignment.Center) {
        if (city.buildings.isEmpty()) {
            Text(
                text = "Start a session to grow your city",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
            return@Box
        }

        Canvas(modifier = Modifier.matchParentSize()) {
            val tier = CityLogicHelper.gridTier(totalMins)
            val gridSize = tier.size
            val r = (gridSize - 1) / 2

            val tileW = size.width / (gridSize + 1)
            val tileH = tileW / 2f
            val hw = tileW / 2f
            val hh = tileH / 2f
            val floorUnit = tileH * 0.55f
            val originX = size.width / 2f
            val originY = size.height * 0.66f

            fun screenX(x: Int, y: Int) = originX + (x - y) * hw
            fun groundY(x: Int, y: Int) = originY + (x + y) * hh

            // render land squares
            for (x in -r..r) {
                for (y in -r..r) {
                    val cx = screenX(x, y)
                    val cy = groundY(x, y)
                    val tile = diamond(cx, cy, hw, hh)
                    drawPath(tile, grass)
                    drawPath(tile, grassEdge, style = Stroke(width = 1f))
                }
            }

            // render buildings
            city.buildings
                .sortedWith(compareBy({ it.x + it.y }, { it.x }))
                .forEach { b ->
                    val hue = BUILDING_HUES[((b.x * 31 + b.y * 17) % 5 + 5) % 5]
                    drawBuilding(
                        cx = screenX(b.x, b.y),
                        cy = groundY(b.x, b.y),
                        hw = hw, hh = hh,
                        floors = b.floors,
                        floorUnit = floorUnit,
                        hue = hue
                    )
                }
        }
    }
}

private fun diamond(cx: Float, cy: Float, hw: Float, hh: Float) = Path().apply {
    moveTo(cx, cy - hh)
    lineTo(cx + hw, cy)
    lineTo(cx, cy + hh)
    lineTo(cx - hw, cy)
    close()
}

private fun DrawScope.drawBuilding(
    cx: Float, cy: Float, hw: Float, hh: Float,
    floors: Int, floorUnit: Float, hue: Color
) {
    val h = floors * floorUnit
    val leftColor = hue.shade(0.80f)
    val rightColor = hue.shade(0.62f)

    drawPath(
        Path().apply {
            moveTo(cx - hw, cy)
            lineTo(cx, cy + hh)
            lineTo(cx, cy + hh - h)
            lineTo(cx - hw, cy - h)
            close()
        },
        leftColor
    )
    // Right face
    drawPath(
        Path().apply {
            moveTo(cx, cy + hh)
            lineTo(cx + hw, cy)
            lineTo(cx + hw, cy - h)
            lineTo(cx, cy + hh - h)
            close()
        },
        rightColor
    )
    // Top face
    val roof = diamond(cx, cy - h, hw, hh)
    drawPath(roof, hue)

    // Floor ridges
    val ridge = hue.shade(0.5f)
    for (f in 1 until floors) {
        val yy = f * floorUnit
        drawLine(ridge, Offset(cx - hw, cy - yy), Offset(cx, cy + hh - yy), strokeWidth = 1f)
        drawLine(ridge, Offset(cx, cy + hh - yy), Offset(cx + hw, cy - yy), strokeWidth = 1f)
    }
    // Roof outline
    drawPath(roof, hue.shade(0.45f), style = Stroke(width = 1.5f))
}
