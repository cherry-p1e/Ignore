package com.caffeinetracker.pk

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * First-order caffeine elimination for one healthy-adult half-life.
 *
 * remaining(t) = sum over doses of doseMg * 0.5 ^ ((t - doseTime) / halfLife)
 *
 * Half-life is 5 hours: the mean plasma half-life for healthy adults in the
 * Institute of Medicine review (NCBI NBK223808) and in StatPearls (NBK519490).
 * Published ranges are wider (about 1.5–9.5 h, or 3–6 h on average in a
 * clinical-toxicology review). Those ranges do not justify a tighter single
 * default than 5 hours.
 *
 * "Effectively gone" is [CLEARANCE_THRESHOLD_MG]: 5 mg left in total.
 * Five half-lives leave 0.5^5 = 3.125% of a dose, and 3.125% of 160 mg is 5 mg.
 */
object CaffeineKinetics {
    const val HALF_LIFE_HOURS = 5.0
    const val HALF_LIFE_MILLIS = HALF_LIFE_HOURS * 60.0 * 60.0 * 1000.0
    const val CLEARANCE_THRESHOLD_MG = 5.0

    /** FDA "up to 400 mg/day" reference, used only as the full-glass visual scale. */
    const val FULL_SCALE_MG = 400.0

    fun remainingMg(doses: List<Dose>, nowMillis: Long): Double {
        var sum = 0.0
        for (dose in doses) {
            val elapsed = (nowMillis - dose.timestampMillis).coerceAtLeast(0).toDouble()
            sum += dose.amountMg * 0.5.pow(elapsed / HALF_LIFE_MILLIS)
        }
        return sum
    }

    /**
     * Milliseconds until [remainingMg] falls under [CLEARANCE_THRESHOLD_MG].
     * First-order elimination is linear, so the sum of doses decays with the
     * same half-life as each dose: t = halfLife * log2(remaining / threshold).
     */
    fun millisUntilCleared(remainingMg: Double): Long {
        if (remainingMg <= CLEARANCE_THRESHOLD_MG) return 0L
        val halfLives = ln(remainingMg / CLEARANCE_THRESHOLD_MG) / ln(2.0)
        return (halfLives * HALF_LIFE_MILLIS).roundToLong()
    }

    fun intakeSinceLocalMidnight(
        doses: List<Dose>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val start = Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        var total = 0
        for (dose in doses) {
            if (dose.timestampMillis in start..nowMillis) {
                total += dose.amountMg
            }
        }
        return total
    }
}
