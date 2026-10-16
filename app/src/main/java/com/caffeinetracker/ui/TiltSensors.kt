package com.caffeinetracker.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicReference

data class TiltSample(val gx: Float, val gy: Float, val gyroZ: Float)

/**
 * Gravity and gyroscope, delivered on a dedicated sensor thread.
 * The UI thread only reads [sample]. Listeners are removed in onPause.
 */
class TiltSensors(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val thread = HandlerThread("CaffeineSensors").also { it.start() }
    private val handler = Handler(thread.looper)
    private val sampleRef = AtomicReference(TiltSample(gx = 0f, gy = 9.81f, gyroZ = 0f))
    private var registered = false

    val sample: TiltSample get() = sampleRef.get()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values
            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY -> sampleRef.updateAndGet { it.copy(gx = values[0], gy = values[1]) }
                Sensor.TYPE_GYROSCOPE -> sampleRef.updateAndGet { it.copy(gyroZ = values[2]) }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (registered) return
        gravity?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        gyroscope?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        registered = true
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(listener)
        sampleRef.updateAndGet { it.copy(gyroZ = 0f) }
        registered = false
    }

    fun close() {
        stop()
        thread.quitSafely()
    }
}

@Composable
fun rememberTiltSensors(): TiltSensors {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sensors = remember { TiltSensors(context.applicationContext) }
    DisposableEffect(lifecycleOwner, sensors) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensors.start()
                Lifecycle.Event.ON_PAUSE -> sensors.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            sensors.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensors.close()
        }
    }
    return sensors
}
