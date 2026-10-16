package com.caffeinetracker.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Glass surface in phone coordinates.
 *
 * Gravity sets the rest angle: the liquid wants to sit perpendicular to the
 * sky direction reported by TYPE_GRAVITY (the same vector an at-rest
 * accelerometer reports). The gyroscope is the phone's angular velocity.
 * The liquid has its own spin, dragged toward the phone and sprung back
 * toward level, so a flick sloshes and then settles.
 *
 * [phi] is the angle of "down" on the screen. 0 points at the bottom edge.
 * Positive [phi] points toward the right edge (right side of the glass lower).
 */
class LiquidPhysics {
    var phi: Float = 0f
        private set
    var wave: Float = 0f
        private set
    var wavePhase: Float = 0f
        private set

    private var liquidWorldSpin = 0f

    fun step(dt: Float, targetPhi: Float, gyroZ: Float) {
        val dtC = dt.coerceIn(0.001f, 0.05f)
        // Clockwise gyro (negative Z) increases phi: the right side drops.
        val containerSpin = -gyroZ
        val error = wrap(phi - targetPhi)
        liquidWorldSpin += (-STIFFNESS * error - DAMPING * liquidWorldSpin) * dtC
        liquidWorldSpin += (containerSpin - liquidWorldSpin) * DRAG * dtC
        phi = wrap(phi + (liquidWorldSpin - containerSpin) * dtC)

        val phoneRate = liquidWorldSpin - containerSpin
        wavePhase += dtC * 5.5f
        val excited = (abs(phoneRate) * 8f + abs(error) * 12f).coerceAtMost(16f)
        wave += (excited - wave) * (1f - exp(-2.5f * dtC))
    }

    companion object {
        private const val STIFFNESS = 45f
        private const val DAMPING = 5f
        private const val DRAG = 1.2f

        /**
         * Rest angle from a TYPE_GRAVITY sample.
         *
         * At rest the gravity sensor points at the sky. Screen axes: +x right,
         * +y down. When the phone is upright, gy is about +9.81 and the angle is 0.
         * When the right side is lower, gx is negative and the angle is positive.
         * A flat phone (almost no in-plane gravity) keeps a level bottom fill.
         */
        fun restAngle(gravityX: Float, gravityY: Float): Float {
            val magnitude = hypot(gravityX, gravityY)
            if (magnitude < 0.8f) return 0f
            val downX = -gravityX / magnitude
            val downY = gravityY / magnitude
            return atan2(downX, downY)
        }

        private fun wrap(angle: Float): Float {
            var value = angle
            val turn = (2.0 * PI).toFloat()
            while (value > PI.toFloat()) value -= turn
            while (value < -PI.toFloat()) value += turn
            return value
        }
    }
}

data class Vec2(val x: Float, val y: Float)

data class LiquidShape(
    val fill: List<Vec2>,
    val surface: List<Vec2>,
)

/**
 * Screen polygon occupied by the coffee. The filled area is the side of a
 * line that gravity (plus slosh) calls "down", sized to [fraction] of the screen.
 * Above a full glass ([fraction] >= 1) the shape is the whole screen, so tilt
 * cannot open a gap.
 */
object LiquidGeometry {
    fun shape(
        width: Float,
        height: Float,
        fraction: Float,
        phi: Float,
        waveAmplitude: Float,
        wavePhase: Float,
    ): LiquidShape {
        if (width <= 0f || height <= 0f || fraction <= 0.0005f) {
            return LiquidShape(emptyList(), emptyList())
        }
        val rect = listOf(
            Pt(0f, 0f, false),
            Pt(width, 0f, false),
            Pt(width, height, false),
            Pt(0f, height, false),
        )
        if (fraction >= 0.9995f) {
            return LiquidShape(rect.map { Vec2(it.x, it.y) }, emptyList())
        }
        val downX = sin(phi)
        val downY = cos(phi)
        val threshold = thresholdForArea(rect, downX, downY, fraction * width * height)
        val clipped = clip(rect, downX, downY, threshold)
        return waveSurface(clipped, downX, downY, waveAmplitude, wavePhase)
    }

    private data class Pt(val x: Float, val y: Float, val onSurface: Boolean)

    private fun thresholdForArea(rect: List<Pt>, downX: Float, downY: Float, area: Float): Float {
        val projections = rect.map { it.x * downX + it.y * downY }
        var low = projections.min() - 2f
        var high = projections.max() + 2f
        repeat(20) {
            val mid = (low + high) / 2f
            val covered = polygonArea(clip(rect, downX, downY, mid))
            if (covered > area) low = mid else high = mid
        }
        return (low + high) / 2f
    }

    private fun clip(poly: List<Pt>, downX: Float, downY: Float, threshold: Float): List<Pt> {
        if (poly.isEmpty()) return poly
        val out = ArrayList<Pt>(poly.size + 2)
        var previous = poly.last()
        var previousIn = previous.x * downX + previous.y * downY >= threshold - 0.01f
        for (current in poly) {
            val currentIn = current.x * downX + current.y * downY >= threshold - 0.01f
            if (currentIn) {
                if (!previousIn) out += intersection(previous, current, downX, downY, threshold)
                out += current.copy(onSurface = abs(current.x * downX + current.y * downY - threshold) < 0.75f)
            } else if (previousIn) {
                out += intersection(previous, current, downX, downY, threshold)
            }
            previous = current
            previousIn = currentIn
        }
        return out
    }

    private fun intersection(a: Pt, b: Pt, downX: Float, downY: Float, threshold: Float): Pt {
        val start = a.x * downX + a.y * downY
        val end = b.x * downX + b.y * downY
        val span = end - start
        val t = if (abs(span) < 1e-4f) 0f else (threshold - start) / span
        return Pt(
            x = a.x + (b.x - a.x) * t,
            y = a.y + (b.y - a.y) * t,
            onSurface = true,
        )
    }

    private fun waveSurface(
        poly: List<Pt>,
        downX: Float,
        downY: Float,
        amplitude: Float,
        phase: Float,
    ): LiquidShape {
        if (poly.size < 3) return LiquidShape(emptyList(), emptyList())
        val fill = ArrayList<Vec2>(poly.size * 2)
        val surface = ArrayList<Vec2>()
        val amp = amplitude.coerceIn(0f, 18f)
        for (index in poly.indices) {
            val start = poly[index]
            val end = poly[(index + 1) % poly.size]
            fill += Vec2(start.x, start.y)
            if (!start.onSurface || !end.onSurface) continue
            val length = hypot(end.x - start.x, end.y - start.y)
            val steps = if (amp < 0.4f || length < 1f) 1 else (length / 14f).toInt().coerceIn(2, 48)
            if (surface.isEmpty()) surface += Vec2(start.x, start.y)
            for (step in 1 until steps) {
                val u = step / steps.toFloat()
                val x = start.x + (end.x - start.x) * u
                val y = start.y + (end.y - start.y) * u
                val offset = sin(phase + u * length * 0.05f) * amp
                // Shift along "up" so the crest crosses the still waterline.
                val point = Vec2(x - downX * offset, y - downY * offset)
                fill += point
                surface += point
            }
            surface += Vec2(end.x, end.y)
        }
        return LiquidShape(fill, surface)
    }

    private fun polygonArea(poly: List<Pt>): Float {
        var sum = 0f
        for (index in poly.indices) {
            val start = poly[index]
            val end = poly[(index + 1) % poly.size]
            sum += start.x * end.y - end.x * start.y
        }
        return abs(sum) * 0.5f
    }
}
