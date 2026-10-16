package com.caffeinetracker.physics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time shallow-water height field for one glass of coffee.
 *
 * Each cell holds a column height. Edges carry the volume flow into the
 * neighbor (inertia). A height difference pushes back (pressure), and the
 * in-plane gravity acceleration pulls toward the low side of the phone.
 * Viscosity damps the flow, so after the phone is held still the waves die
 * and the surface settles. Transfers are clamped, so the caffeine volume is
 * conserved by the fluid step; [enforceVolume] then adds or removes the small
 * difference to match the pharmacokinetic fill.
 *
 * Foam is a second field riding on wet cells. It is visual only: a full
 * layer drains to nothing in [FOAM_LIFETIME_SECONDS], not over the 5-hour
 * caffeine half-life.
 */
class CoffeeFluid(
    val columns: Int = 32,
    val rows: Int = 56,
) {
    private val count = columns * rows
    private val h = FloatArray(count)
    private val foam = FloatArray(count)
    private val flowRight = FloatArray((columns - 1) * rows)
    private val flowDown = FloatArray(columns * (rows - 1))
    private val foamScratch = FloatArray(count)
    private val foamOutgoing = FloatArray(count)
    private val motion = FloatArray(count)
    private var pendingPour = false

    fun height(x: Int, y: Int): Float = h[index(x, y)]

    fun foam(x: Int, y: Int): Float = foam[index(x, y)]

    fun isShore(x: Int, y: Int): Boolean {
        val i = index(x, y)
        if (h[i] <= DRY) return false
        if (x > 0 && h[index(x - 1, y)] <= DRY) return true
        if (x + 1 < columns && h[index(x + 1, y)] <= DRY) return true
        if (y > 0 && h[index(x, y - 1)] <= DRY) return true
        if (y + 1 < rows && h[index(x, y + 1)] <= DRY) return true
        return false
    }

    fun totalVolume(): Float {
        var sum = 0f
        for (value in h) sum += value
        return sum
    }

    fun kineticEnergy(): Float {
        var sum = 0f
        for (value in flowRight) sum += value * value
        for (value in flowDown) sum += value * value
        return sum
    }

    internal fun debugSetFoam(x: Int, y: Int, value: Float) {
        foam[index(x, y)] = value
    }

    fun meanFoam(): Float {
        var sum = 0f
        for (value in foam) sum += value
        return sum / count
    }

    /** A new dose: crema appears on the next step, then collapses on its own. */
    fun pour() {
        pendingPour = true
    }

    /**
     * @param gravityX TYPE_GRAVITY x, m/s², device axis pointing right
     * @param gravityY TYPE_GRAVITY y, m/s², device axis pointing up
     * @param targetFraction remaining caffeine / 400 mg, clamped by the caller to 0..1
     */
    fun step(
        dt: Float,
        gravityX: Float,
        gravityY: Float,
        gyroX: Float,
        gyroY: Float,
        gyroZ: Float,
        targetFraction: Float,
    ) {
        val frameDt = dt.coerceIn(0f, 0.05f)
        if (frameDt <= 0f) return
        val (ax, ay) = screenAcceleration(gravityX, gravityY)
        motion.fill(0f)
        var left = frameDt
        while (left > 1e-4f) {
            val sub = min(left, SUBSTEP_SECONDS)
            integrate(sub, ax, ay, gyroX, gyroY, gyroZ)
            left -= sub
        }
        enforceVolume(targetFraction.coerceIn(0f, 1f) * capacity(), ax, ay)
        if (pendingPour) {
            pendingPour = false
            depositPourFoam()
        }
        depositDisturbanceFoam(frameDt)
        advectFoam(frameDt, ax, ay)
        decayFoam(frameDt)
    }

    private fun integrate(dt: Float, ax: Float, ay: Float, gyroX: Float, gyroY: Float, gyroZ: Float) {
        val damp = exp(-VISCOSITY * dt)
        val yaw = gyroZ.coerceIn(-MAX_GYRO, MAX_GYRO)
        val pitch = gyroX.coerceIn(-MAX_GYRO, MAX_GYRO)
        val roll = gyroY.coerceIn(-MAX_GYRO, MAX_GYRO)
        for (y in 0 until rows) {
            val yNorm = (y + 0.5f) / rows - 0.5f
            for (x in 0 until columns - 1) {
                val left = index(x, y)
                val right = index(x + 1, y)
                val edge = rightEdge(x, y)
                val pinned = pinnedPair(h[left], h[right])
                if (pinned) {
                    flowRight[edge] *= damp
                    continue
                }
                val dh = h[left] - h[right]
                // Positive yaw lags as a horizontal shear; roll rate shoves left/right.
                val gyroKick = (-yaw * yNorm + roll * 0.15f) * GYRO_GAIN
                var flow = flowRight[edge] + (PRESSURE * dh + GRAVITY_GAIN * ax + gyroKick) * dt
                flow *= damp
                flow = stopAtLid(flow, sourceHeight = h[left], destinationHeight = h[right])
                flowRight[edge] = flow.coerceIn(-MAX_FLOW, MAX_FLOW)
            }
        }
        for (y in 0 until rows - 1) {
            for (x in 0 until columns) {
                val top = index(x, y)
                val bottom = index(x, y + 1)
                val edge = downEdge(x, y)
                val pinned = pinnedPair(h[top], h[bottom])
                if (pinned) {
                    flowDown[edge] *= damp
                    continue
                }
                val xNorm = (x + 0.5f) / columns - 0.5f
                val dh = h[top] - h[bottom]
                val gyroKick = (yaw * xNorm + pitch * 0.15f) * GYRO_GAIN
                var flow = flowDown[edge] + (PRESSURE * dh + GRAVITY_GAIN * ay + gyroKick) * dt
                flow *= damp
                flow = stopAtLid(flow, sourceHeight = h[top], destinationHeight = h[bottom])
                flowDown[edge] = flow.coerceIn(-MAX_FLOW, MAX_FLOW)
            }
        }
        applyFlows(dt)
    }

    private fun applyFlows(dt: Float) {
        val outgoing = FloatArray(count)
        for (y in 0 until rows) {
            for (x in 0 until columns - 1) {
                val delta = flowRight[rightEdge(x, y)] * dt
                if (delta > 0f) outgoing[index(x, y)] += delta else outgoing[index(x + 1, y)] -= delta
            }
        }
        for (y in 0 until rows - 1) {
            for (x in 0 until columns) {
                val delta = flowDown[downEdge(x, y)] * dt
                if (delta > 0f) outgoing[index(x, y)] += delta else outgoing[index(x, y + 1)] -= delta
            }
        }
        val scale = FloatArray(count) { cell ->
            if (outgoing[cell] > h[cell] && outgoing[cell] > 1e-6f) h[cell] / outgoing[cell] else 1f
        }
        for (y in 0 until rows) {
            for (x in 0 until columns - 1) {
                val delta = flowRight[rightEdge(x, y)] * dt
                if (delta >= 0f) transfer(index(x, y), index(x + 1, y), delta * scale[index(x, y)])
                else transfer(index(x + 1, y), index(x, y), -delta * scale[index(x + 1, y)])
            }
        }
        for (y in 0 until rows - 1) {
            for (x in 0 until columns) {
                val delta = flowDown[downEdge(x, y)] * dt
                if (delta >= 0f) transfer(index(x, y), index(x, y + 1), delta * scale[index(x, y)])
                else transfer(index(x, y + 1), index(x, y), -delta * scale[index(x, y + 1)])
            }
        }
    }

    private fun transfer(source: Int, destination: Int, requested: Float) {
        if (requested <= 0f) return
        val room = MAX_HEIGHT - h[destination]
        val moved = min(requested, min(h[source], max(0f, room)))
        if (moved <= 0f) return
        h[source] -= moved
        h[destination] += moved
        motion[source] += moved
        motion[destination] += moved
    }

    private fun enforceVolume(target: Float, ax: Float, ay: Float) {
        repeat(columns + rows + 2) {
            val current = totalVolume()
            val diff = target - current
            if (abs(diff) < 1e-3f) return
            if (diff < 0f) {
                if (current <= 1e-5f) {
                    h.fill(0f)
                    return
                }
                val scale = target / current
                for (i in h.indices) h[i] *= scale
                return
            }
            if (current < 1e-3f) {
                seed(target, ax, ay)
                return@repeat
            }
            var left = diff
            var wetSum = 0f
            for (value in h) if (value > DRY) wetSum += value
            if (wetSum > 0f) {
                for (i in h.indices) {
                    if (h[i] <= DRY) continue
                    val put = min(MAX_HEIGHT - h[i], left * (h[i] / wetSum))
                    if (put > 0f) {
                        h[i] += put
                        left -= put
                    }
                }
            }
            if (left <= 1e-3f) return
            val frontier = ArrayList<Int>()
            for (y in 0 until rows) {
                for (x in 0 until columns) {
                    if (h[index(x, y)] <= DRY) continue
                    if (x > 0 && h[index(x - 1, y)] <= DRY) frontier.add(index(x - 1, y))
                    if (x + 1 < columns && h[index(x + 1, y)] <= DRY) frontier.add(index(x + 1, y))
                    if (y > 0 && h[index(x, y - 1)] <= DRY) frontier.add(index(x, y - 1))
                    if (y + 1 < rows && h[index(x, y + 1)] <= DRY) frontier.add(index(x, y + 1))
                }
            }
            if (frontier.isEmpty()) return
            val share = left / frontier.size
            var placed = false
            for (cell in frontier) {
                val put = min(MAX_HEIGHT - h[cell], share)
                if (put > 0f) {
                    h[cell] += put
                    placed = true
                }
            }
            if (!placed) return
        }
    }

    private fun seed(target: Float, ax: Float, ay: Float) {
        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val score = ax * x + ay * y
                if (score > bestScore) {
                    bestScore = score
                    best = index(x, y)
                }
            }
        }
        h[best] = min(MAX_HEIGHT, target)
    }

    private fun depositPourFoam() {
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val cell = index(x, y)
                if (h[cell] <= DRY) continue
                val cover = if (isShore(x, y)) 1f else 0.72f
                if (foam[cell] < cover) foam[cell] = cover
            }
        }
    }

    private fun depositDisturbanceFoam(dt: Float) {
        val limit = DISTURBANCE_THRESHOLD * dt
        for (i in motion.indices) {
            if (h[i] <= DRY || motion[i] <= limit) continue
            val added = (motion[i] / dt - DISTURBANCE_THRESHOLD) * DISTURBANCE_GAIN
            foam[i] = min(1f, foam[i] + added)
        }
    }

    /**
     * Donor-cell advection. Foam moves with the liquid and also slides toward
     * the low side. Flux stops at a dry neighbor, so crema piles on the shore
     * instead of falling off the glass or being recreated at the far wall.
     */
    private fun advectFoam(dt: Float, ax: Float, ay: Float) {
        val gravity = hypot(ax, ay)
        val driftX = if (gravity < 0.6f) 0f else ax / gravity * FOAM_DRIFT_CELLS_PER_SEC
        val driftY = if (gravity < 0.6f) 0f else ay / gravity * FOAM_DRIFT_CELLS_PER_SEC
        foamOutgoing.fill(0f)
        for (y in 0 until rows) {
            for (x in 0 until columns - 1) {
                val left = index(x, y)
                val right = index(x + 1, y)
                if (h[left] <= DRY || h[right] <= DRY) continue
                val speed = edgeSpeed(horizontalSpeed(x, y), horizontalSpeed(x + 1, y), driftX)
                foamOutgoing[if (speed >= 0f) left else right] += abs(speed) * dt
            }
        }
        for (y in 0 until rows - 1) {
            for (x in 0 until columns) {
                val top = index(x, y)
                val bottom = index(x, y + 1)
                if (h[top] <= DRY || h[bottom] <= DRY) continue
                val speed = edgeSpeed(verticalSpeed(x, y), verticalSpeed(x, y + 1), driftY)
                foamOutgoing[if (speed >= 0f) top else bottom] += abs(speed) * dt
            }
        }
        for (i in foamOutgoing.indices) {
            if (foamOutgoing[i] > 1f) foamOutgoing[i] = 1f / foamOutgoing[i]
            else foamOutgoing[i] = 1f
        }
        foam.copyInto(foamScratch)
        for (y in 0 until rows) {
            for (x in 0 until columns - 1) {
                val left = index(x, y)
                val right = index(x + 1, y)
                if (h[left] <= DRY || h[right] <= DRY) continue
                val speed = edgeSpeed(horizontalSpeed(x, y), horizontalSpeed(x + 1, y), driftX)
                if (speed >= 0f) moveFoam(left, right, speed * dt) else moveFoam(right, left, -speed * dt)
            }
        }
        for (y in 0 until rows - 1) {
            for (x in 0 until columns) {
                val top = index(x, y)
                val bottom = index(x, y + 1)
                if (h[top] <= DRY || h[bottom] <= DRY) continue
                val speed = edgeSpeed(verticalSpeed(x, y), verticalSpeed(x, y + 1), driftY)
                if (speed >= 0f) moveFoam(top, bottom, speed * dt) else moveFoam(bottom, top, -speed * dt)
            }
        }
        for (i in foam.indices) {
            foam[i] = if (h[i] <= DRY) 0f else foamScratch[i].coerceIn(0f, 1f)
        }
    }

    private fun edgeSpeed(a: Float, b: Float, drift: Float): Float = (a + b) * 0.5f + drift

    private fun moveFoam(from: Int, to: Int, fraction: Float) {
        val moved = foam[from] * fraction * foamOutgoing[from]
        foamScratch[from] -= moved
        foamScratch[to] += moved
    }

    private fun decayFoam(dt: Float) {
        val drop = dt / FOAM_LIFETIME_SECONDS
        for (i in foam.indices) {
            foam[i] = max(0f, foam[i] - drop)
            if (h[i] <= DRY) foam[i] = 0f
        }
    }

    private fun horizontalSpeed(x: Int, y: Int): Float {
        val left = if (x > 0) flowRight[rightEdge(x - 1, y)] else 0f
        val right = if (x + 1 < columns) flowRight[rightEdge(x, y)] else 0f
        val depth = max(h[index(x, y)], 0.2f)
        return ((left + right) * 0.5f / depth).coerceIn(-MAX_FLOW, MAX_FLOW)
    }

    private fun verticalSpeed(x: Int, y: Int): Float {
        val above = if (y > 0) flowDown[downEdge(x, y - 1)] else 0f
        val below = if (y + 1 < rows) flowDown[downEdge(x, y)] else 0f
        val depth = max(h[index(x, y)], 0.2f)
        return ((above + below) * 0.5f / depth).coerceIn(-MAX_FLOW, MAX_FLOW)
    }

    /** Flow into an already full cell has nowhere to go, so it must not keep spinning. */
    private fun stopAtLid(flow: Float, sourceHeight: Float, destinationHeight: Float): Float {
        val intoRightOrDown = flow > 0f && destinationHeight >= MAX_HEIGHT - 0.02f
        val intoLeftOrUp = flow < 0f && sourceHeight >= MAX_HEIGHT - 0.02f
        return if (intoRightOrDown || intoLeftOrUp) flow * 0.15f else flow
    }

    private fun pinnedPair(a: Float, b: Float): Boolean {
        val bothDry = a <= DRY && b <= DRY
        val bothFull = a >= MAX_HEIGHT - 0.002f && b >= MAX_HEIGHT - 0.002f
        return bothDry || bothFull
    }

    private fun capacity(): Float = count * MAX_HEIGHT

    private fun index(x: Int, y: Int): Int = y * columns + x

    private fun rightEdge(x: Int, y: Int): Int = y * (columns - 1) + x

    private fun downEdge(x: Int, y: Int): Int = y * columns + x

    companion object {
        const val MAX_HEIGHT = 1f

        /** A saturated crema cell reaches zero after this many seconds. Visual only. */
        const val FOAM_LIFETIME_SECONDS = 100f

        private const val DRY = 0.015f
        private const val PRESSURE = 18f
        private const val GRAVITY_GAIN = 0.42f
        private const val VISCOSITY = 4.2f
        private const val GYRO_GAIN = 2.4f
        private const val MAX_FLOW = 8f
        private const val MAX_GYRO = 8f
        private const val SUBSTEP_SECONDS = 1f / 90f
        private const val DISTURBANCE_THRESHOLD = 0.65f
        private const val DISTURBANCE_GAIN = 0.35f
        private const val FOAM_DRIFT_CELLS_PER_SEC = 3.2f

        /**
         * TYPE_GRAVITY points at the sky. Screen axes are +x right and +y down,
         * so the acceleration of the coffee is (-gravityX, +gravityY).
         */
        fun screenAcceleration(gravityX: Float, gravityY: Float): Pair<Float, Float> {
            return -gravityX to gravityY
        }
    }
}
