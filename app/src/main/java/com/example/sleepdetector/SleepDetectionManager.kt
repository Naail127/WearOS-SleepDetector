package com.example.sleepdetector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import kotlin.math.abs

class SleepDetectionManager(private val context: Context) : SensorEventListener {

    private var sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private var audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // --- Configuration Variables ---
    private val changeThresholdPercentage = 0.10f // 10% change
    private val requiredDurationMs: Long = 5 * 60 * 1000L // 5 minutes in milliseconds

    // --- State Tracking ---
    private var baselineHeartRate: Float = 0f
    private var readingsCount: Int = 0
    private var isSustainingChange: Boolean = false
    private var timeOfFirstChange: Long = 0

    fun startListening() {
        if (heartRateSensor == null) {
            Log.e("SleepDetector", "No heart rate sensor found on this Wear OS device.")
            return
        }
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val currentHeartRate = event.values[0]

            // Establish a baseline from the first 10 readings
            if (readingsCount < 10) {
                baselineHeartRate = ((baselineHeartRate * readingsCount) + currentHeartRate) / (readingsCount + 1)
                readingsCount++
                return
            }

            checkForSleep(currentHeartRate)
        }
    }

    private fun checkForSleep(currentHeartRate: Float) {
        val difference = abs(currentHeartRate - baselineHeartRate)
        val percentageChange = difference / baselineHeartRate

        if (percentageChange >= changeThresholdPercentage) {
            val currentTime = System.currentTimeMillis()

            if (!isSustainingChange) {
                isSustainingChange = true
                timeOfFirstChange = currentTime
            } else {
                val timeElapsed = currentTime - timeOfFirstChange

                if (timeElapsed >= requiredDurationMs) {
                    stopAudioPlayback()
                    stopListening() // Sleep detected, stop monitoring to save battery
                }
            }
        } else {
            if (isSustainingChange) {
                isSustainingChange = false
                timeOfFirstChange = 0
            }
        }
    }

    private fun stopAudioPlayback() {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()

        audioManager.requestAudioFocus(focusRequest)
        Log.d("SleepDetector", "Audio stopped.")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}