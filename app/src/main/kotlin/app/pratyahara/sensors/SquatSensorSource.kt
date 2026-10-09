package app.pratyahara.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.pratyahara.core.reps.SquatCounter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

data class RepProgress(val reps: Int, val shaking: Boolean, val depth: Float = 0f)

/** Streams accelerometer samples into a [SquatCounter] while the squat screen is open. */
class SquatSensorSource(context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accel: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val available: Boolean get() = accel != null

    fun reps(counter: SquatCounter): Flow<RepProgress> = callbackFlow {
        val sensor = accel ?: run { close(); return@callbackFlow }
        var lastSentNanos = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val before = counter.reps
                val wasShaking = counter.shaking
                counter.onSample(e.timestamp, e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                // Rep and shake changes go out at once; the depth meter updates about 20 times a second.
                val changed = counter.reps != before || counter.shaking != wasShaking
                if (changed || e.timestamp - lastSentNanos > 50_000_000L) {
                    lastSentNanos = e.timestamp
                    trySend(RepProgress(counter.reps, counter.shaking, counter.depth.toFloat()))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        trySend(RepProgress(counter.reps, counter.shaking, 0f))
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensors.unregisterListener(listener) }
    }.conflate()
}
