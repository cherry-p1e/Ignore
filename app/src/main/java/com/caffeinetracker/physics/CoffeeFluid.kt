package com.caffeinetracker.physics

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time shallow-water height field for one glass of coffee.
 *
 * Each cell is a column of coffee. Edges remember how fast volume is moving,
 * so a tilt sloshes instead of snapping. Gravity packs the coffee against the
 * low side of the phone: held upright, that side is the bottom of the screen
 * and the free surface is a level line. Viscosity damps the flow after the
 * phone is held still. A mild surface-tension term rounds the shoreline.
 *
 * Volume matches remaining caffeine. Extra coffee is taken off the uphill
 * surface of liquid that is already resting in the glass, so the waterline
 * drops and the body stays full. A splash that is still in the air is not
 * erased; it falls first. Foam is a second field and is visual only.
 */
class CoffeeFluid(
    val columns: Int = 72,
    val rows: Int = 148,
) {
    private val count = columns * rows
    private val h = FloatArray(count)
    private val foam = FloatArray(count)
    private val flowRight = FloatArray((columns - 1) * rows)
    private val flowDown = FloatArray(columns * (rows - 1))
    private val foamScratch = FloatArray(count)
    private val foamOutgoing = FloatArray(count)
    private val motion = FloatArray(count)
    private val outgoing = FloatArray(count)
    private val sendLimit = FloatArray(count)
    private val lap = FloatArray(count)
    private val score = FloatArray(count)
    private val order = IntArray(count)
    private val sortKeys = LongArray(count)
    private val supported = BooleanArray(count)
    private var pendingPour = false

    private val waveSpeed = 0.50f * rows
    private val pressure = waveSpeed * waveSpeed
    private val gravityGain = DROP_ACROSS_GLASS * pressure / (9.81f * rows)
    private val maxFlow = 1.15f * rows
    private val gyroGain = 0.45f * maxFlow
    private val foamDrift = 0.62f * columns.toFloat()
    private val substepSeconds = min(0.42f / waveSpeed, 0.42f / maxFlow).coerceAtMost(1f / 90f)

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

    private fun wetCentroidY(): Float {
        var mass = 0f
        var moment = 0f
        for (y in 0 until rows) {
            var rowMass = 0f
            val row = y * columns
            for (x in 0 until columns) rowMass += h[row + x]
            mass += rowMass
            moment += rowMass * y
        }
        return if (mass < 1e-3f) (rows - 1) * 0.5f else moment / mass
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

    internal fun debugSetHeight(x: Int, y: Int, value: Float) {
        h[index(x, y)] = value
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
     * @param gravityX TYPE_GRAVITY x, m/s², device axis pointing right in natural orientation
     * @param gravityY TYPE_GRAVITY y, m/s², device axis pointing up in natural orientation
     * @param rotation [android.view.Surface] rotation: 0, 1, 2, or 3
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
        rotation: Int = 0,
    ) {
        val frameDt = dt.coerceIn(0f, 0.05f)
        if (frameDt <= 0f) return
        val (ax, ay) = screenAcceleration(gravityX, gravityY, rotation)
        motion.fill(0f)
        var left = frameDt
        while (left > 1e-4f) {
            val sub = min(left, substepSeconds)
            integrate(sub, ax, ay, gyroX, gyroY, gyroZ)
            left -= sub
        }
        enforceVolume(targetFraction.coerceIn(0f, 1f) * capacity(), ax, ay)
        if (pendingPour) {
            pendingPour = false
            depositPourFoam()
        }
        depositDisturbanceFoam()
        advectFoam(frameDt, ax, ay)
        decayFoam(frameDt)
    }

    private fun integrate(dt: Float, ax: Float, ay: Float, gyroX: Float, gyroY: Float, gyroZ: Float) {
        computeLaplacian()
        val damp = exp(-VISCOSITY * dt)
        val yaw = gyroZ.coerceIn(-MAX_GYRO, MAX_GYRO)
        val pitch = gyroX.coerceIn(-MAX_GYRO, MAX_GYRO)
        val roll = gyroY.coerceIn(-MAX_GYRO, MAX_GYRO)
        val gravityX = gravityGain * ax
        val gravityY = gravityGain * ay
        val centerY = wetCentroidY()
        for (y in 0 until rows) {
            val yOff = (y - centerY) / rows
            val row = y * columns
            for (x in 0 until columns - 1) {
                val left = row + x
                val right = left + 1
                val edge = rightEdge(x, y)
                if (pinnedPair(h[left], h[right])) {
                    flowRight[edge] *= damp
                    continue
                }
                val dh = (h[left] - h[right]) + TENSION * (lap[right] - lap[left])
                val wet = max(h[left], h[right]).coerceIn(0f, 1f)
                // Yaw shears around the body of coffee, so a half glass still sloshes
                // when its surface sits near the middle of the screen.
                val gyroKick = (-yaw * yOff + roll * 0.25f) * gyroGain
                var flow = flowRight[edge] + (pressure * dh + gravityX * wet + gyroKick) * dt
                flow *= damp
                flow = stopIntoFull(flow, h[left], h[right])
                flow = stopFromDry(flow, h[left], h[right])
                flowRight[edge] = flow.coerceIn(-maxFlow, maxFlow)
            }
        }
        for (y in 0 until rows - 1) {
            val row = y * columns
            for (x in 0 until columns) {
                val top = row + x
                val bottom = top + columns
                val edge = downEdge(x, y)
                if (pinnedPair(h[top], h[bottom])) {
                    flowDown[edge] *= damp
                    continue
                }
                val xNorm = (x + 0.5f) / columns - 0.5f
                val dh = (h[top] - h[bottom]) + TENSION * (lap[bottom] - lap[top])
                val wet = max(h[top], h[bottom]).coerceIn(0f, 1f)
                val gyroKick = (yaw * xNorm + pitch * 0.25f) * gyroGain
                var flow = flowDown[edge] + (pressure * dh + gravityY * wet + gyroKick) * dt
                flow *= damp
                flow = stopIntoFull(flow, h[top], h[bottom])
                flow = stopFromDry(flow, h[top], h[bottom])
                flowDown[edge] = flow.coerceIn(-maxFlow, maxFlow)
            }
        }
        applyFlows(dt)
    }

    private fun computeLaplacian() {
        for (y in 0 until rows) {
            val row = y * columns
            for (x in 0 until columns) {
                val i = row + x
                var sum = 0f
                var n = 0
                if (x > 0) {
                    sum += h[i - 1]
                    n++
                }
                if (x + 1 < columns) {
                    sum += h[i + 1]
                    n++
                }
                if (y > 0) {
                    sum += h[i - columns]
                    n++
                }
                if (y + 1 < rows) {
                    sum += h[i + columns]
                    n++
                }
                lap[i] = sum - n * h[i]
            }
        }
    }

    private fun applyFlows(dt: Float) {
        outgoing.fill(0f)
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
        for (cell in 0 until count) {
            val out = outgoing[cell]
            sendLimit[cell] = if (out > h[cell] && out > 1e-6f) h[cell] / out else 1f
        }
        for (y in 0 until rows) {
            for (x in 0 until columns - 1) {
                val delta = flowRight[rightEdge(x, y)] * dt
                if (delta >= 0f) transfer(index(x, y), index(x + 1, y), delta * sendLimit[index(x, y)])
                else transfer(index(x + 1, y), index(x, y), -delta * sendLimit[index(x + 1, y)])
            }
        }
        for (y in 0 until rows - 1) {
            for (x in 0 until columns) {
                val delta = flowDown[downEdge(x, y)] * dt
                if (delta >= 0f) transfer(index(x, y), index(x, y + 1), delta * sendLimit[index(x, y)])
                else transfer(index(x, y + 1), index(x, y), -delta * sendLimit[index(x, y + 1)])
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
        val diff = target - totalVolume()
        if (abs(diff) < 1e-3f) return
        prepareScores(ax, ay)
        if (diff > 0f) addVolume(diff, ax, ay) else peelSupportedExcess(-diff, ax, ay)
    }

    /**
     * Pour new coffee onto the lowest open cells so the surface rises as a
     * line instead of a blob growing from the middle.
     */
    private fun addVolume(amount: Float, ax: Float, ay: Float) {
        var left = amount
        sortAll(descending = true)
        val band = levelBand(ax, ay)
        var i = 0
        while (i < count && left > 1e-4f) {
            val base = score[order[i]]
            var j = i + 1
            while (j < count && base - score[order[j]] <= band) j++
            var roomSum = 0f
            for (k in i until j) {
                val cell = order[k]
                if (canReceive(cell)) roomSum += MAX_HEIGHT - h[cell]
            }
            if (roomSum > 1e-5f) {
                val give = min(left, roomSum)
                for (k in i until j) {
                    val cell = order[k]
                    if (!canReceive(cell)) continue
                    val room = MAX_HEIGHT - h[cell]
                    val put = give * (room / roomSum)
                    if (put > 0f) {
                        h[cell] += put
                        left -= put
                    }
                }
            }
            i = j
        }
    }

    private fun canReceive(cell: Int): Boolean {
        if (h[cell] >= MAX_HEIGHT - 1e-3f) return false
        val down = steeperNeighbor(cell)
        if (down < 0) return true
        return h[down] >= MAX_HEIGHT - 0.04f
    }

    /**
     * Drop the waterline. Only liquid that is already resting on the glass
     * is removed, and it is removed from the uphill surface first. A body of
     * coffee above empty space is left alone so the next steps can drop it.
     */
    private fun peelSupportedExcess(excess: Float, ax: Float, ay: Float) {
        sortAll(descending = true)
        supported.fill(false)
        for (i in 0 until count) {
            val cell = order[i]
            if (h[cell] <= DRY) continue
            supported[cell] = restingOnGlass(cell)
        }
        var n = 0
        for (cell in 0 until count) {
            if (supported[cell] && h[cell] > DRY) order[n++] = cell
        }
        if (n == 0) return
        sortPrefix(n, descending = false)
        val band = levelBand(ax, ay)
        var left = excess
        var i = 0
        while (left > 1e-4f && i < n) {
            val base = score[order[i]]
            var j = i + 1
            while (j < n && score[order[j]] - base <= band) j++
            var bandVolume = 0f
            for (k in i until j) bandVolume += h[order[k]]
            if (bandVolume <= left + 1e-5f) {
                for (k in i until j) clearCell(order[k])
                left -= bandVolume
            } else {
                val frac = left / bandVolume
                for (k in i until j) {
                    val cell = order[k]
                    h[cell] -= h[cell] * frac
                    if (h[cell] <= DRY) clearCell(cell)
                }
                left = 0f
            }
            i = j
        }
    }

    private fun restingOnGlass(cell: Int): Boolean {
        val x = cell % columns
        val y = cell / columns
        val mine = score[cell]
        var downhill = false
        var supportedBelow = false
        fun look(nx: Int, ny: Int) {
            if (nx < 0 || ny < 0 || nx >= columns || ny >= rows) return
            val neighbor = index(nx, ny)
            if (score[neighbor] <= mine + 1e-4f) return
            downhill = true
            if (supported[neighbor] && h[neighbor] >= SUPPORTED_HEIGHT) supportedBelow = true
        }
        look(x - 1, y)
        look(x + 1, y)
        look(x, y - 1)
        look(x, y + 1)
        if (!downhill) return true
        return supportedBelow
    }

    private fun clearCell(cell: Int) {
        h[cell] = 0f
        foam[cell] = 0f
    }

    private fun prepareScores(ax: Float, ay: Float) {
        for (y in 0 until rows) {
            val rowScore = ay * y
            val row = y * columns
            for (x in 0 until columns) score[row + x] = ax * x + rowScore
        }
    }

    private fun sortAll(descending: Boolean) {
        for (i in 0 until count) order[i] = i
        sortPrefix(count, descending)
    }

    private fun sortPrefix(n: Int, descending: Boolean) {
        for (i in 0 until n) {
            val cell = order[i]
            sortKeys[i] = packKey(score[cell], cell, descending)
        }
        Arrays.sort(sortKeys, 0, n)
        for (i in 0 until n) order[i] = sortKeys[i].toInt()
    }

    private fun packKey(value: Float, index: Int, descending: Boolean): Long {
        var bits = value.toRawBits()
        bits = bits xor (bits shr 31 and 0x7fffffff)
        if (descending) bits = bits.inv()
        return (bits.toLong() shl 32) or (index.toLong() and 0xffffffffL)
    }

    private fun levelBand(ax: Float, ay: Float): Float {
        val g = hypot(ax, ay)
        return if (g < 0.6f) 0.05f else 0.35f * g
    }

    /** Neighbor that is strictly farther downhill, or -1 when this cell is the floor. */
    private fun steeperNeighbor(cell: Int): Int {
        val x = cell % columns
        val y = cell / columns
        var best = -1
        var bestScore = score[cell] + 1e-4f
        fun consider(nx: Int, ny: Int) {
            if (nx < 0 || ny < 0 || nx >= columns || ny >= rows) return
            val neighbor = index(nx, ny)
            if (score[neighbor] > bestScore) {
                bestScore = score[neighbor]
                best = neighbor
            }
        }
        consider(x - 1, y)
        consider(x + 1, y)
        consider(x, y - 1)
        consider(x, y + 1)
        return best
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

    private fun depositDisturbanceFoam() {
        for (i in motion.indices) {
            if (h[i] <= DRY || motion[i] <= DISTURBANCE_CELLS) continue
            val added = (motion[i] - DISTURBANCE_CELLS) * DISTURBANCE_GAIN
            foam[i] = min(1f, foam[i] + added)
        }
    }

    /**
     * Donor-cell advection. Foam moves with the liquid and also slides toward
     * the low side. Flux stops at a dry neighbor, so crema piles on the shore
     * instead of falling off the glass.
     */
    private fun advectFoam(dt: Float, ax: Float, ay: Float) {
        val gravity = hypot(ax, ay)
        val driftX = if (gravity < 0.6f) 0f else ax / gravity * foamDrift
        val driftY = if (gravity < 0.6f) 0f else ay / gravity * foamDrift
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
            if (foamOutgoing[i] > 1f) foamOutgoing[i] = 1f / foamOutgoing[i] else foamOutgoing[i] = 1f
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
        return ((left + right) * 0.5f / depth).coerceIn(-maxFlow, maxFlow)
    }

    private fun verticalSpeed(x: Int, y: Int): Float {
        val above = if (y > 0) flowDown[downEdge(x, y - 1)] else 0f
        val below = if (y + 1 < rows) flowDown[downEdge(x, y)] else 0f
        val depth = max(h[index(x, y)], 0.2f)
        return ((above + below) * 0.5f / depth).coerceIn(-maxFlow, maxFlow)
    }

    /** A dry cell has nothing to send. Phantom current above the surface should die. */
    private fun stopFromDry(flow: Float, leftOrTop: Float, rightOrBottom: Float): Float {
        val source = if (flow > 0f) leftOrTop else rightOrBottom
        return if (source <= DRY) 0f else flow
    }

    /** Positive flow moves toward [rightOrBottom]. Block it when that side is already full. */
    private fun stopIntoFull(flow: Float, leftOrTop: Float, rightOrBottom: Float): Float {
        val destinationFull = if (flow > 0f) {
            rightOrBottom >= MAX_HEIGHT - 0.02f
        } else {
            leftOrTop >= MAX_HEIGHT - 0.02f
        }
        return if (destinationFull) 0f else flow
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
        private const val SUPPORTED_HEIGHT = 0.92f
        private const val VISCOSITY = 1.15f
        private const val TENSION = 0.035f
        private const val MAX_GYRO = 8f
        private const val DISTURBANCE_CELLS = 0.18f
        private const val DISTURBANCE_GAIN = 2.2f

        /**
         * How many cell-heights the free surface would fall across the glass
         * if columns were not clamped. Large enough that coffee packs against
         * the low edge and the leftover is a thin surface band.
         */
        private const val DROP_ACROSS_GLASS = 18f

        /**
         * TYPE_GRAVITY points at the sky in the device's natural orientation
         * (+x right, +y up). [rotation] is [android.view.Surface.getRotation]:
         * 0 upright, 1 rotated 90° counterclockwise, 2 upside down, 3 the other way.
         * The returned acceleration is in screen space, +x right and +y down,
         * so an upright phone sends coffee toward the bottom edge.
         */
        fun screenAcceleration(gravityX: Float, gravityY: Float, rotation: Int = 0): Pair<Float, Float> {
            val (right, up) = when (rotation) {
                1 -> -gravityY to gravityX
                2 -> -gravityX to -gravityY
                3 -> gravityY to -gravityX
                else -> gravityX to gravityY
            }
            return -right to up
        }
    }
}
