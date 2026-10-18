package com.caffeinetracker.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import com.caffeinetracker.physics.CoffeeFluid

private const val COFFEE_DEEP = 0x2A160E
private const val COFFEE_BODY = 0x4A2C1A
private const val COFFEE_SHALLOW = 0x6B422C
private const val CREMA_RGB = 0xE7D3B0
private val BubbleLight = Color(0xFFF8F1E3)
private val BubbleShadow = Color(0xFFC4A574)

/**
 * The simulation grid is finer than the old 32×56 field, and the body is
 * drawn as one filtered image so the shoreline does not crawl as a staircase
 * of rectangles. Bubbles stay vector circles on top of that image.
 */
private class LiquidRaster(val columns: Int, val rows: Int) {
    private val pixels = IntArray(columns * rows)
    private val bitmap: Bitmap = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
    val image: ImageBitmap = bitmap.asImageBitmap()

    fun paint(fluid: CoffeeFluid) {
        var i = 0
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val depth = fluid.height(x, y)
                pixels[i] = if (depth <= 0.02f) {
                    0xFF000000.toInt()
                } else {
                    val surface = y == 0 || fluid.height(x, y - 1) <= 0.02f
                    coffeeArgb(depth, fluid.foam(x, y), surface)
                }
                i++
            }
        }
        bitmap.setPixels(pixels, 0, columns, 0, 0, columns, rows)
    }
}

@Composable
fun CoffeeLiquid(
    fluid: CoffeeFluid,
    frame: Int,
    modifier: Modifier = Modifier,
) {
    val raster = remember(fluid.columns, fluid.rows) { LiquidRaster(fluid.columns, fluid.rows) }
    Canvas(modifier) {
        if (frame < 0) return@Canvas
        if (size.width <= 1f || size.height <= 1f) return@Canvas
        raster.paint(fluid)
        drawImage(
            image = raster.image,
            dstSize = IntSize(
                size.width.toInt().coerceAtLeast(1),
                size.height.toInt().coerceAtLeast(1),
            ),
            filterQuality = FilterQuality.Medium,
        )
        drawFoam(fluid, size.width / fluid.columns, size.height / fluid.rows)
    }
}

private fun DrawScope.drawFoam(fluid: CoffeeFluid, cellW: Float, cellH: Float) {
    for (y in 0 until fluid.rows) {
        for (x in 0 until fluid.columns) {
            val foam = fluid.foam(x, y)
            if (foam <= 0.12f || fluid.height(x, y) <= 0.02f) continue
            val shore = fluid.isShore(x, y)
            if (!shore && hash(x, y, 1) % 5 != 0) continue
            val bubbles = 1 + hash(x, y, 2) % if (shore) 3 else 2
            val left = x * cellW
            val top = y * cellH
            for (bubble in 0 until bubbles) {
                val roll = hash(x, y, 10 + bubble)
                val radius = cellW * (0.16f + (roll and 255) / 255f * if (shore) 0.55f else 0.34f)
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

private fun coffeeArgb(depth: Float, foam: Float, surface: Boolean): Int {
    val t = (depth / CoffeeFluid.MAX_HEIGHT).coerceIn(0f, 1f)
    var color = if (t < 0.45f) {
        lerpRgb(COFFEE_SHALLOW, COFFEE_BODY, t / 0.45f)
    } else {
        lerpRgb(COFFEE_BODY, COFFEE_DEEP, (t - 0.45f) / 0.55f)
    }
    if (surface) color = lerpRgb(color, COFFEE_SHALLOW, 0.55f)
    if (foam > 0.04f) color = lerpRgb(color, CREMA_RGB, (foam * 0.82f).coerceIn(0f, 0.9f))
    return color or 0xFF000000.toInt()
}

private fun lerpRgb(from: Int, to: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    val fr = (from shr 16) and 255
    val fg = (from shr 8) and 255
    val fb = from and 255
    val tr = (to shr 16) and 255
    val tg = (to shr 8) and 255
    val tb = to and 255
    val r = (fr + (tr - fr) * t).toInt()
    val g = (fg + (tg - fg) * t).toInt()
    val b = (fb + (tb - fb) * t).toInt()
    return (r shl 16) or (g shl 8) or b
}

private fun hash(x: Int, y: Int, salt: Int): Int {
    var n = x * 374761393 + y * 668265263 + salt * 1442695041
    n = (n xor (n ushr 13)) * 1274126177
    return (n xor (n ushr 16)) and 0x7fffffff
}
