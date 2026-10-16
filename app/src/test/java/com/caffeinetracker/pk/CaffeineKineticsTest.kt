package com.caffeinetracker.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CaffeineKineticsTest {
    @Test
    fun halfLife_cutsOneDoseInHalf() {
        val start = 1_700_000_000_000L
        val doses = listOf(Dose(id = "a", timestampMillis = start, amountMg = 200))
        val later = start + CaffeineKinetics.HALF_LIFE_MILLIS.toLong()
        assertEquals(100.0, CaffeineKinetics.remainingMg(doses, later), 0.01)
    }

    @Test
    fun doses_addIndependently() {
        val start = 1_700_000_000_000L
        val doses = listOf(
            Dose(id = "a", timestampMillis = start, amountMg = 80),
            Dose(id = "b", timestampMillis = start, amountMg = 80),
        )
        val later = start + CaffeineKinetics.HALF_LIFE_MILLIS.toLong()
        assertEquals(80.0, CaffeineKinetics.remainingMg(doses, later), 0.01)
    }

    @Test
    fun fiveHalfLivesOf160mg_isTheClearanceThreshold() {
        val remaining = 160.0 * 0.5.powExactFive()
        assertEquals(CaffeineKinetics.CLEARANCE_THRESHOLD_MG, remaining, 0.0001)
        val millis = CaffeineKinetics.millisUntilCleared(160.0)
        assertEquals((5 * CaffeineKinetics.HALF_LIFE_MILLIS).toLong(), millis, 2L)
    }

    @Test
    fun atOrBelowThreshold_needsNoMoreTime() {
        assertEquals(0L, CaffeineKinetics.millisUntilCleared(5.0))
        assertEquals(0L, CaffeineKinetics.millisUntilCleared(4.9))
    }

    @Test
    fun countdown_matchesTheSummedRemaining() {
        val now = 5_000_000L
        val doses = listOf(
            Dose(id = "old", timestampMillis = now - CaffeineKinetics.HALF_LIFE_MILLIS.toLong(), amountMg = 100),
            Dose(id = "new", timestampMillis = now, amountMg = 50),
        )
        val remaining = CaffeineKinetics.remainingMg(doses, now)
        assertEquals(100.0, remaining, 0.05)
        val fromSum = CaffeineKinetics.millisUntilCleared(remaining)
        val fromHundred = CaffeineKinetics.millisUntilCleared(100.0)
        assertEquals(fromHundred, fromSum, 2L)
    }

    @Test
    fun todayTotal_usesLocalMidnight_andDoesNotDecay() {
        val zone = ZoneOffset.UTC
        val now = ZonedDateTime.of(2026, 10, 16, 0, 30, 0, 0, zone).toInstant().toEpochMilli()
        val yesterday = ZonedDateTime.of(2026, 10, 15, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val today = ZonedDateTime.of(2026, 10, 16, 0, 10, 0, 0, zone).toInstant().toEpochMilli()
        val doses = listOf(
            Dose(id = "y", timestampMillis = yesterday, amountMg = 200),
            Dose(id = "t", timestampMillis = today, amountMg = 95),
        )
        assertEquals(95, CaffeineKinetics.intakeSinceLocalMidnight(doses, now, zone))
    }

    @Test
    fun doseCodec_roundTrips_andDropsGarbage() {
        val dose = Dose(id = "n1", timestampMillis = 1_700_000_000_000L, amountMg = 180)
        assertEquals(dose, Dose.decode(Dose.encode(dose)))
        assertNull(Dose.decode("nope"))
        assertNull(Dose.decode("10:0:x"))
    }

    private fun Double.powExactFive(): Double = 0.5 * 0.5 * 0.5 * 0.5 * 0.5

    private fun assertEquals(expected: Long, actual: Long, tolerance: Long) {
        val delta = kotlin.math.abs(expected - actual)
        if (delta > tolerance) {
            org.junit.Assert.fail("expected $expected ± $tolerance but was $actual")
        }
    }
}
