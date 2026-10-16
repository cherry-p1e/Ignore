package com.caffeinetracker.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class LiquidPhysicsTest {
    @Test
    fun uprightGravity_restsLevel() {
        assertEquals(0f, LiquidPhysics.restAngle(0f, 9.81f), 0.001f)
    }

    @Test
    fun rightSideDown_pointsDownToTheRight() {
        // Sky is toward -X when the right edge is the low edge.
        val phi = LiquidPhysics.restAngle(gravityX = -9.81f, gravityY = 0f)
        assertEquals((PI / 2.0).toFloat(), phi, 0.001f)
    }

    @Test
    fun leftSideDown_pointsDownToTheLeft() {
        val phi = LiquidPhysics.restAngle(gravityX = 9.81f, gravityY = 0f)
        assertEquals((-PI / 2.0).toFloat(), phi, 0.001f)
    }

    @Test
    fun flatPhone_staysABottomFill() {
        assertEquals(0f, LiquidPhysics.restAngle(0f, 0.2f), 0.001f)
    }

    @Test
    fun heldTilt_settlesOnTheRestAngle() {
        val physics = LiquidPhysics()
        val target = 0.4f
        val dt = 1f / 60f
        repeat(180) { physics.step(dt, target, gyroZ = 0f) }
        assertEquals(target, physics.phi, 0.02f)
    }

    @Test
    fun clockwiseFlick_thenStop_overshootsTowardTheRight() {
        val physics = LiquidPhysics()
        val dt = 1f / 60f
        var target = 0f
        val gyroZ = -2.5f
        val containerSpin = -gyroZ
        repeat(24) {
            target += containerSpin * dt
            physics.step(dt, target, gyroZ)
        }
        val lagAtStop = physics.phi - target
        var peak = lagAtStop
        repeat(90) {
            physics.step(dt, target, gyroZ = 0f)
            peak = maxOf(peak, physics.phi - target)
        }
        assertTrue("surface should pass the new rest angle, peak=$peak", peak > 0.05f)
    }

    @Test
    fun halfFull_levelGlass_fillsTheBottomHalf() {
        val shape = LiquidGeometry.shape(100f, 200f, fraction = 0.5f, phi = 0f, waveAmplitude = 0f, wavePhase = 0f)
        assertEquals(10_000f, area(shape.fill), 80f)
        assertEquals(100f, shape.fill.minOf { it.y }, 1.5f)
        assertEquals(200f, shape.fill.maxOf { it.y }, 0.5f)
    }

    @Test
    fun upsideDown_fillsFromTheTop() {
        val shape = LiquidGeometry.shape(
            width = 100f,
            height = 200f,
            fraction = 0.25f,
            phi = PI.toFloat(),
            waveAmplitude = 0f,
            wavePhase = 0f,
        )
        assertEquals(5_000f, area(shape.fill), 120f)
        assertEquals(0f, shape.fill.minOf { it.y }, 1.5f)
        assertEquals(50f, shape.fill.maxOf { it.y }, 2f)
    }

    @Test
    fun fullGlass_coversTheScreen() {
        val shape = LiquidGeometry.shape(80f, 160f, fraction = 1f, phi = 0.4f, waveAmplitude = 12f, wavePhase = 1f)
        assertEquals(80f * 160f, area(shape.fill), 1f)
        assertTrue(shape.surface.isEmpty())
    }

    @Test
    fun emptyGlass_drawsNothing() {
        val shape = LiquidGeometry.shape(80f, 160f, fraction = 0f, phi = 0.2f, waveAmplitude = 4f, wavePhase = 0f)
        assertTrue(shape.fill.isEmpty())
    }

    private fun area(poly: List<Vec2>): Float {
        if (poly.size < 3) return 0f
        var sum = 0f
        for (index in poly.indices) {
            val start = poly[index]
            val end = poly[(index + 1) % poly.size]
            sum += start.x * end.y - end.x * start.y
        }
        return abs(sum) * 0.5f
    }
}
