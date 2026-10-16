package com.caffeinetracker.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class CoffeeFluidTest {
    @Test
    fun uprightGravity_pullsTowardTheBottomOfTheScreen() {
        val (ax, ay) = CoffeeFluid.screenAcceleration(gravityX = 0f, gravityY = 9.81f)
        assertEquals(0f, ax, 0.001f)
        assertTrue(ay > 9f)
    }

    @Test
    fun rightSideDown_pullsTowardTheRight() {
        val (ax, ay) = CoffeeFluid.screenAcceleration(gravityX = -9.81f, gravityY = 0f)
        assertTrue(ax > 9f)
        assertEquals(0f, ay, 0.001f)
    }

    @Test
    fun halfGlass_settlesOnTheLowSide_andKeepsItsVolume() {
        val fluid = CoffeeFluid(columns = 12, rows = 20)
        val dt = 1f / 60f
        repeat(360) {
            fluid.step(dt, gravityX = 0f, gravityY = 9.81f, gyroX = 0f, gyroY = 0f, gyroZ = 0f, targetFraction = 0.5f)
        }
        val capacity = 12f * 20f * CoffeeFluid.MAX_HEIGHT
        assertEquals(capacity * 0.5f, fluid.totalVolume(), capacity * 0.02f)
        val top = bandVolume(fluid, 0, 6)
        val bottom = bandVolume(fluid, 13, 20)
        assertTrue("top band should be nearly dry, was $top", top < fluid.totalVolume() * 0.12f)
        assertTrue("bottom band should hold the coffee, was $bottom of ${fluid.totalVolume()}", bottom > fluid.totalVolume() * 0.55f)
        assertTrue("waves should have settled, kinetic=${fluid.kineticEnergy()}", fluid.kineticEnergy() < 6f)
    }

    @Test
    fun fullGlass_coversEveryCell() {
        val fluid = CoffeeFluid(columns = 8, rows = 10)
        repeat(30) {
            fluid.step(1f / 60f, 0f, 9.81f, 0f, 0f, 0f, targetFraction = 1f)
        }
        for (y in 0 until fluid.rows) {
            for (x in 0 until fluid.columns) {
                assertTrue(fluid.height(x, y) > 0.9f)
            }
        }
    }

    @Test
    fun gyroImpulse_stirsTheGlass_thenTheMotionDecays() {
        val fluid = CoffeeFluid(columns = 12, rows = 16)
        val dt = 1f / 60f
        repeat(180) { fluid.step(dt, 0f, 9.81f, 0f, 0f, 0f, 0.45f) }
        val settled = fluid.kineticEnergy()
        repeat(20) { fluid.step(dt, 0f, 9.81f, gyroX = 0f, gyroY = 0f, gyroZ = 6f, targetFraction = 0.45f) }
        val stirred = fluid.kineticEnergy()
        repeat(180) { fluid.step(dt, 0f, 9.81f, 0f, 0f, 0f, 0.45f) }
        val after = fluid.kineticEnergy()
        assertTrue("gyro should add motion ($settled -> $stirred)", stirred > settled + 1f)
        assertTrue("held still, motion should fall ($stirred -> $after)", after < stirred * 0.5f)
        assertEquals(12f * 16f * CoffeeFluid.MAX_HEIGHT * 0.45f, fluid.totalVolume(), 1.5f)
    }

    @Test
    fun pourFoam_isGoneAfterItsVisualLifetime_notTheCaffeineHalfLife() {
        val fluid = CoffeeFluid(columns = 8, rows = 10)
        fluid.step(1f / 30f, 0f, 9.81f, 0f, 0f, 0f, 0.6f)
        fluid.pour()
        fluid.step(1f / 30f, 0f, 0f, 0f, 0f, 0f, 0.6f)
        assertTrue(fluid.meanFoam() > 0.4f)
        val steps = (CoffeeFluid.FOAM_LIFETIME_SECONDS / 0.05f).toInt() + 5
        repeat(steps) {
            fluid.step(0.05f, gravityX = 0f, gravityY = 0f, gyroX = 0f, gyroY = 0f, gyroZ = 0f, targetFraction = 0.6f)
        }
        assertTrue("foam should collapse in about ${CoffeeFluid.FOAM_LIFETIME_SECONDS}s, mean=${fluid.meanFoam()}", fluid.meanFoam() < 0.08f)
    }

    @Test
    fun foam_driftsTowardTheLowSide() {
        val fluid = CoffeeFluid(columns = 16, rows = 6)
        repeat(40) {
            fluid.step(1f / 60f, 0f, 0f, 0f, 0f, 0f, targetFraction = 1f)
        }
        for (y in 0 until fluid.rows) {
            for (x in 0 until fluid.columns) {
                fluid.debugSetFoam(x, y, if (x < 4) 1f else 0f)
            }
        }
        val before = foamCentroidX(fluid)
        repeat(150) {
            // Right side down: gravity sensor x is negative, so coffee accelerates to screen +x.
            fluid.step(1f / 60f, gravityX = -9.81f, gravityY = 0f, gyroX = 0f, gyroY = 0f, gyroZ = 0f, targetFraction = 1f)
        }
        val after = foamCentroidX(fluid)
        assertTrue(
            "foam centroid should move right ($before -> $after), mean=${fluid.meanFoam()}, " +
                "h0=${fluid.height(0, 0)}, h15=${fluid.height(15, 0)}, vol=${fluid.totalVolume()}",
            after > before + 1f,
        )
    }

    private fun bandVolume(fluid: CoffeeFluid, y0: Int, y1: Int): Float {
        var sum = 0f
        for (y in y0 until y1) {
            for (x in 0 until fluid.columns) sum += fluid.height(x, y)
        }
        return sum
    }

    private fun foamCentroidX(fluid: CoffeeFluid): Float {
        var moment = 0f
        var mass = 0f
        for (y in 0 until fluid.rows) {
            for (x in 0 until fluid.columns) {
                val foam = fluid.foam(x, y)
                moment += foam * x
                mass += foam
            }
        }
        if (mass <= 1e-4f) return 0f
        return moment / mass
    }
}
