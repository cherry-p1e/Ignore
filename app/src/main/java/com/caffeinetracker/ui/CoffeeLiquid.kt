package com.caffeinetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.caffeinetracker.physics.LiquidGeometry
import com.caffeinetracker.physics.LiquidShape
import com.caffeinetracker.physics.Vec2

private val CoffeeDeep = Color(0xFF2A160E)
private val CoffeeBody = Color(0xFF4A2C1A)
private val CoffeeSurface = Color(0xFF6B422C)
private val Meniscus = Color(0xFFE7D3B0)

@Composable
fun CoffeeLiquid(
    fraction: Float,
    phi: Float,
    wave: Float,
    wavePhase: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawRect(Color.Black)
        val shape = LiquidGeometry.shape(
            width = size.width,
            height = size.height,
            fraction = fraction,
            phi = phi,
            waveAmplitude = wave,
            wavePhase = wavePhase,
        )
        drawCoffee(shape)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoffee(shape: LiquidShape) {
    if (shape.fill.size < 3) return
    val path = Path().apply {
        moveTo(shape.fill.first().x, shape.fill.first().y)
        for (point in shape.fill.drop(1)) lineTo(point.x, point.y)
        close()
    }
    val top = shape.fill.minOf(Vec2::y)
    drawPath(
        path,
        brush = Brush.verticalGradient(
            colors = listOf(CoffeeSurface, CoffeeBody, CoffeeDeep),
            startY = top,
            endY = size.height,
        ),
    )
    if (shape.surface.size < 2) return
    val meniscus = Path().apply {
        moveTo(shape.surface.first().x, shape.surface.first().y)
        for (point in shape.surface.drop(1)) lineTo(point.x, point.y)
    }
    drawPath(
        meniscus,
        color = Meniscus,
        style = Stroke(width = 3.5f, cap = StrokeCap.Round),
    )
    drawPath(
        meniscus,
        color = Meniscus.copy(alpha = 0.35f),
        style = Stroke(width = 10f, cap = StrokeCap.Round),
    )
}
