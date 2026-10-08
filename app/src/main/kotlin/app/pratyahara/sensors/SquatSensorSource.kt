package app.pratyahara.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.pratyahara.core.reps.RepCounter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class RepProgress(val reps: Int, val shaking: Boolean)

/** Streams accelerometer samples into a [RepCounter] while the squat screen is open. */
class SquatSensorSource(context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accel: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val available: Boolean get() = accel != null

    fun reps(counter: RepCounter = RepCounter()): Flow<RepProgress> = callbackFlow {
        val sensor = accel ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val before = counter.reps
                val wasShaking = counter.shaking
                counter.onSample(e.timestamp, e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                if (counter.reps != before || counter.shaking != wasShaking) {
                    trySend(RepProgress(counter.reps, counter.shaking))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        trySend(RepProgress(counter.reps, counter.shaking))
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensors.unregisterListener(listener) }
    }
}
