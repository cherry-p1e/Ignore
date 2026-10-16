package com.caffeinetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.caffeinetracker.physics.CoffeeFluid
import kotlin.math.min

private val CoffeeDeep = Color(0xFF2A160E)
private val CoffeeBody = Color(0xFF4A2C1A)
private val CoffeeShallow = Color(0xFF6B422C)
private val Crema = Color(0xFFE7D3B0)
private val BubbleLight = Color(0xFFF8F1E3)
private val BubbleShadow = Color(0xFFC4A574)

@Composable
fun CoffeeLiquid(
    fluid: CoffeeFluid,
    frame: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        // [frame] changes every simulation step so the canvas repaints.
        if (frame < 0) return@Canvas
        drawRect(Color.Black)
        if (size.width <= 1f || size.height <= 1f) return@Canvas
        val cellW = size.width / fluid.columns
        val cellH = size.height / fluid.rows
        drawBody(fluid, cellW, cellH)
        drawFoam(fluid, cellW, cellH)
    }
}

private fun DrawScope.drawBody(fluid: CoffeeFluid, cellW: Float, cellH: Float) {
    for (y in 0 until fluid.rows) {
        for (x in 0 until fluid.columns) {
            val depth = fluid.height(x, y)
            if (depth <= 0.02f) continue
            val foam = fluid.foam(x, y)
            drawRect(
                color = coffeeColor(depth, foam),
                topLeft = Offset(x * cellW, y * cellH),
                size = Size(cellW + 0.6f, cellH + 0.6f),
            )
            if (!fluid.isShore(x, y)) continue
            drawRect(
                color = Crema.copy(alpha = 0.55f),
                topLeft = Offset(x * cellW, y * cellH),
                size = Size(cellW + 0.6f, min(3.5f, cellH * 0.28f)),
            )
        }
    }
}

private fun DrawScope.drawFoam(fluid: CoffeeFluid, cellW: Float, cellH: Float) {
    for (y in 0 until fluid.rows) {
        for (x in 0 until fluid.columns) {
            val foam = fluid.foam(x, y)
            if (foam <= 0.12f || fluid.height(x, y) <= 0.02f) continue
            val shore = fluid.isShore(x, y)
            if (!shore && hash(x, y, 1) % 3 != 0) continue
            val bubbles = 1 + hash(x, y, 2) % if (shore) 3 else 2
            val left = x * cellW
            val top = y * cellH
            for (bubble in 0 until bubbles) {
                val roll = hash(x, y, 10 + bubble)
                val radius = cellW * (0.10f + (roll and 255) / 255f * if (shore) 0.34f else 0.22f)
                val cx = left + ((hash(x, y, 30 + bubble) and 255) / 255f) * cellW
                val cy = top + ((hash(x, y, 50 + bubble) and 255) / 255f) * cellH
                val alpha = (0.35f + 0.65f * foam).coerceIn(0f, 1f)
                drawCircle(
                    color = BubbleShadow.copy(alpha = alpha * 0.85f),
                    radius = radius,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = BubbleLight.copy(alpha = alpha),
                    radius = radius * 0.62f,
                    center = Offset(cx - radius * 0.18f, cy - radius * 0.2f),
                )
            }
        }
    }
}

private fun coffeeColor(depth: Float, foam: Float): Color {
    val t = (depth / CoffeeFluid.MAX_HEIGHT).coerceIn(0f, 1f)
    val body = if (t < 0.45f) {
        lerp(CoffeeShallow, CoffeeBody, t / 0.45f)
    } else {
        lerp(CoffeeBody, CoffeeDeep, (t - 0.45f) / 0.55f)
    }
    if (foam <= 0.04f) return body
    return lerp(body, Crema, (foam * 0.82f).coerceIn(0f, 0.9f))
}

private fun lerp(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f,
    )
}

private fun hash(x: Int, y: Int, salt: Int): Int {
    var n = x * 374761393 + y * 668265263 + salt * 1442695041
    n = (n xor (n ushr 13)) * 1274126177
    return (n xor (n ushr 16)) and 0x7fffffff
}
