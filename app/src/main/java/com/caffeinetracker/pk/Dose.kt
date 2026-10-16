package com.caffeinetracker.pk

/**
 * One logged intake. [amountMg] is what was swallowed; it does not decay.
 * The blood estimate is computed later from [timestampMillis].
 */
data class Dose(
    val id: String,
    val timestampMillis: Long,
    val amountMg: Int,
) {
    companion object {
        /** Stored form is "timestamp:mg:id". Malformed rows are skipped. */
        fun decode(raw: String): Dose? {
            val parts = raw.split(':')
            if (parts.size != 3) return null
            val timestamp = parts[0].toLongOrNull() ?: return null
            val amount = parts[1].toIntOrNull() ?: return null
            if (amount <= 0 || timestamp <= 0L || parts[2].isEmpty()) return null
            return Dose(id = parts[2], timestampMillis = timestamp, amountMg = amount)
        }

        fun encode(dose: Dose): String = "${dose.timestampMillis}:${dose.amountMg}:${dose.id}"
    }
}
