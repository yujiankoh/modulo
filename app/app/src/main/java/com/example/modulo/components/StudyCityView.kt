package com.example.modulo.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modulo.Building
import com.example.modulo.City
import com.example.modulo.R
import com.example.modulo.helpers.CityLogicHelper
import kotlin.math.max
import kotlin.math.min

private const val W = 80f        // tile width
private const val H = 40f        // tile height (2:1 iso)
private const val FLOOR_H = 22f  // px per building floor
private const val BLD_W = 24f    // building footprint half-width, 60% of the tile
private const val BLD_H = 12f    // building footprint half-height (keeps 2:1)
private const val BH_MAX = 12f   // biggest footprint half-height
private const val SOIL_DEPTH = 26f

private val MAST_COLOR = Color(0xFF9AA0A6)
private val BEACON_COLOR = Color(0xFFE5484D)

@Composable
fun StudyCityView(
    city: City,
    totalMins: Int,
    scheme: CityScheme,
    onCycleScheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) scheme.night else scheme.day

    val tier = CityLogicHelper.gridTier(totalMins)
    val r = (tier.size - 1) / 2
    val maxFloors = max(3, city.buildings.filter { it.floors > 0 }.maxOfOrNull { it.floors } ?: 0)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (city.buildings.isEmpty()) {
                Text(
                    text = "Start a session to grow your city",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            } else {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val fit = cityFit(size.width, size.height, r, maxFloors)
                    withTransform({
                        translate(fit.offX, fit.offY)
                        scale(fit.scale, fit.scale, pivot = Offset.Zero)
                    }) {
                        drawCityScene(city, r, palette)
                    }
                }
            }
        }

        // Scheme change button
        Button(
            onClick = onCycleScheme,
            modifier = Modifier
                .offset(y = (-64).dp)
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.3f),
                contentColor = scheme.accent
            ),
            border = BorderStroke(1.dp, scheme.accent)
        ) {
            Icon(
                painter = painterResource(R.drawable.rotate),
                contentDescription = "Colour scheme",
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.padding(3.dp))
            Text(scheme.name, fontSize = 12.sp)
        }
    }
}

private fun iso(x: Int, y: Int) = Offset(((x - y) * W) / 2f, ((x + y) * H) / 2f)

private data class CityFit(val scale: Float, val offX: Float, val offY: Float)

private fun cityFit(width: Float, height: Float, r: Int, maxFloors: Int): CityFit {
    val leftC = iso(-r, r); val rightC = iso(r, -r); val top = iso(-r, -r); val bottom = iso(r, r)
    val sandE = W / 2f + W * 0.35f
    val skyline = maxFloors * FLOOR_H + BH_MAX + 18f
    val minX = leftC.x - sandE - 90f
    val maxX = rightC.x + sandE + 90f
    val minY = top.y - H / 2f - H * 0.35f - skyline - 24f
    val maxY = bottom.y + H / 2f + H * 0.35f + SOIL_DEPTH + 56f
    val worldW = maxX - minX
    val worldH = maxY - minY
    val s = min(width / worldW, height / worldH)
    return CityFit(
        scale = s,
        offX = (width - worldW * s) / 2f - minX * s,
        offY = (height - worldH * s) / 2f - minY * s,
    )
}

private fun diamond(cx: Float, cy: Float, w2: Float, h2: Float) = listOf(
    Offset(cx, cy - h2), Offset(cx + w2, cy), Offset(cx, cy + h2), Offset(cx - w2, cy)
)

private fun DrawScope.fillPoly(points: List<Offset>, color: Color, alpha: Float = 1f) {
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        close()
    }
    drawPath(path, color, alpha = alpha)
}

private fun DrawScope.strokePoly(points: List<Offset>, color: Color, width: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        close()
    }
    drawPath(path, color, alpha = alpha, style = Stroke(width = width))
}

private fun DrawScope.strokeLine(points: List<Offset>, color: Color, width: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(path, color, alpha = alpha, style = Stroke(width = width, cap = StrokeCap.Round))
}

private fun DrawScope.drawCityScene(city: City, r: Int, pal: CityPalette) {
    val top = iso(-r, -r); val rightC = iso(r, -r); val bottom = iso(r, r); val leftC = iso(-r, r)
    val sandE = W / 2f + W * 0.35f

    // --- water decoration (behind the island) ---
    fun wave(wx: Float, wy: Float, sc: Float = 1f, op: Float = 1f) {
        val p = Path().apply {
            moveTo(wx, wy)
            relativeQuadraticTo(12 * sc, 7 * sc, 24 * sc, 0f)
            relativeQuadraticTo(12 * sc, -7 * sc, 24 * sc, 0f)
        }
        drawPath(p, pal.wave, alpha = op, style = Stroke(width = 3 * sc, cap = StrokeCap.Round))
    }
    wave(leftC.x - W - 50f, leftC.y + 30f)
    wave(leftC.x - W - 110f, leftC.y - 16f, 0.7f, 0.7f)
    wave(leftC.x - W + 4f, leftC.y - 52f, 0.85f, 0.85f)
    wave(leftC.x - W - 30f, leftC.y + 78f, 0.6f, 0.55f)
    wave(rightC.x + W / 2f + 30f, rightC.y + 40f)
    wave(rightC.x + W / 2f + 70f, rightC.y - 36f, 0.85f, 0.85f)
    wave(rightC.x + W / 2f + 120f, rightC.y + 6f, 0.6f, 0.6f)
    wave(rightC.x + W / 2f + 44f, rightC.y + 92f, 0.7f, 0.55f)
    wave(top.x + 70f, top.y - H - 54f)
    wave(top.x - 96f, top.y - H - 30f, 0.7f, 0.65f)
    wave(bottom.x - 120f, bottom.y + H + 46f, 0.8f, 0.7f)
    wave(bottom.x + 88f, bottom.y + H + 62f, 0.65f, 0.6f)

    val southY = bottom.y + H / 2f + H * 0.35f
    fillPoly(
        listOf(
            Offset(leftC.x - sandE, leftC.y), Offset(bottom.x, southY),
            Offset(bottom.x, southY + SOIL_DEPTH), Offset(leftC.x - sandE, leftC.y + SOIL_DEPTH)
        ), pal.soil
    )
    fillPoly(
        listOf(
            Offset(rightC.x + sandE, rightC.y), Offset(bottom.x, southY),
            Offset(bottom.x, southY + SOIL_DEPTH), Offset(rightC.x + sandE, rightC.y + SOIL_DEPTH)
        ), pal.soil, alpha = 0.75f
    )

    val sand = listOf(
        Offset(top.x, top.y - H / 2f - H * 0.35f),
        Offset(rightC.x + W / 2f + W * 0.35f, rightC.y),
        Offset(bottom.x, southY),
        Offset(leftC.x - W / 2f - W * 0.35f, leftC.y)
    )
    fillPoly(sand, pal.sand)

    val land = listOf(
        Offset(top.x, top.y - H / 2f),
        Offset(rightC.x + W / 2f, rightC.y),
        Offset(bottom.x, bottom.y + H / 2f),
        Offset(leftC.x - W / 2f, leftC.y)
    )
    fillPoly(land, pal.grass)

    // Checkered grass tiles so the grid reads without hard lines.
    for (x in -r..r) {
        for (y in -r..r) {
            val c = iso(x, y)
            val tone = if ((x + y) % 2 == 0) pal.grass else pal.grassAlt
            fillPoly(diamond(c.x, c.y, W / 2f, H / 2f), tone)
        }
    }
    strokePoly(sand, Color.Black, 1.5f, 0.18f)
    strokePoly(land, Color.Black, 1.2f, 0.15f)

    city.buildings
        .filter { it.floors > 0 }
        .sortedBy { it.x + it.y }
        .forEach { drawTower(it, pal) }
}

private fun DrawScope.drawTower(b: Building, pal: CityPalette) {
    val base = iso(b.x, b.y)
    val sx = base.x; val sy = base.y
    val ht = b.floors * FLOOR_H
    val hue = pal.buildings[((b.x * 31 + b.y * 17) % 5 + 5) % 5]

    val left = listOf(
        Offset(sx - BLD_W, sy - ht), Offset(sx, sy - ht + BLD_H),
        Offset(sx, sy + BLD_H), Offset(sx - BLD_W, sy)
    )
    val right = listOf(
        Offset(sx, sy - ht + BLD_H), Offset(sx + BLD_W, sy - ht),
        Offset(sx + BLD_W, sy), Offset(sx, sy + BLD_H)
    )
    val roof = diamond(sx, sy - ht, BLD_W, BLD_H)

    fillPoly(left, hue)
    fillPoly(left, Color.Black, 0.18f)       // shade left face
    fillPoly(right, hue)
    fillPoly(right, Color.Black, 0.32f)      // shade right face darker
    fillPoly(roof, hue)
    fillPoly(roof, Color.White, 0.30f)       // lit roof

    // Windows: two columns per face, one row per floor, following each face's slope.
    val winH = 5.5f
    val cols = listOf(0.18f to 0.42f, 0.58f to 0.82f)
    for (i in 0 until b.floors) {
        val mid = sy - i * FLOOR_H - FLOOR_H / 2f
        for ((t1, t2) in cols) {
            // Left face edge: t walks outer-corner (0) → back-centre (1).
            val la = Offset(sx - BLD_W + t1 * BLD_W, mid + t1 * BLD_H)
            val lb = Offset(sx - BLD_W + t2 * BLD_W, mid + t2 * BLD_H)
            fillPoly(
                listOf(
                    Offset(la.x, la.y - winH), Offset(lb.x, lb.y - winH),
                    Offset(lb.x, lb.y + winH), Offset(la.x, la.y + winH)
                ), pal.window
            )
            // Right face edge.
            val ra = Offset(sx + t1 * BLD_W, mid + BLD_H - t1 * BLD_H)
            val rb = Offset(sx + t2 * BLD_W, mid + BLD_H - t2 * BLD_H)
            fillPoly(
                listOf(
                    Offset(ra.x, ra.y - winH), Offset(rb.x, rb.y - winH),
                    Offset(rb.x, rb.y + winH), Offset(ra.x, ra.y + winH)
                ), pal.window
            )
        }
    }

    strokeLine(
        listOf(Offset(sx - BLD_W, sy - ht), Offset(sx, sy - ht + BLD_H), Offset(sx + BLD_W, sy - ht)),
        Color.White, 1.2f, 0.45f
    )
    strokeLine(listOf(Offset(sx, sy - ht + BLD_H), Offset(sx, sy + BLD_H)), Color.White, 1.2f, 0.25f)
    // Dark outer corners define the silhouette (right darker, matching the shading).
    strokeLine(listOf(Offset(sx - BLD_W, sy - ht), Offset(sx - BLD_W, sy)), Color.Black, 1.2f, 0.20f)
    strokeLine(listOf(Offset(sx + BLD_W, sy - ht), Offset(sx + BLD_W, sy)), Color.Black, 1.2f, 0.32f)

    // Antenna + beacon on tall towers (5+ floors).
    if (b.floors >= 5) {
        val topY = sy - ht - BLD_H
        strokeLine(listOf(Offset(sx, topY + 5f), Offset(sx, topY - 11f)), MAST_COLOR, 1.6f, 1f)
        drawCircle(BEACON_COLOR, radius = 2f, center = Offset(sx, topY - 13f))
    }
}
