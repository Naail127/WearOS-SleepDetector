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
import kotlin.math.sqrt

class SleepDetectionManager(private val context: Context) : SensorEventListener {

    private var sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // --- Configuration Variables ---
    private val changeThresholdPercentage = 0.10f // 10% change
    private val requiredDurationMs: Long = 5 * 60 * 1000L // 5 minutes in milliseconds
    private val shakeThreshold = 12.0f // Adjust based on how hard the user should shake

    // --- State Tracking ---
    private var baselineHeartRate: Float = 0f
    private var readingsCount: Int = 0
    private var isSustainingChange: Boolean = false
    private var timeOfFirstChange: Long = 0
    private var isSleepModeActive = false
    private var lastShakeTime: Long = 0
    private var focusRequest: AudioFocusRequest? = null

    fun startListening() {
        startHeartRateMonitoring()
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        isSleepModeActive = false
    }

    private fun startHeartRateMonitoring() {
        if (heartRateSensor == null) {
            Log.e("SleepDetector", "No heart rate sensor found.")
            return
        }
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d("SleepDetector", "Heart rate monitoring started.")
    }

    private fun stopHeartRateMonitoring() {
        sensorManager.unregisterListener(this, heartRateSensor)
    }

    private fun startAccelerometerMonitoring() {
        if (accelerometer == null) {
            Log.e("SleepDetector", "No accelerometer found.")
            return
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        Log.d("SleepDetector", "Accelerometer monitoring started for shake detection.")
    }

    private fun stopAccelerometerMonitoring() {
        sensorManager.unregisterListener(this, accelerometer)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                if (!isSleepModeActive) {
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
            Sensor.TYPE_ACCELEROMETER -> {
                if (isSleepModeActive) {
                    detectShake(event.values)
                }
            }
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
                    activateSleepMode()
                }
            }
        } else {
            if (isSustainingChange) {
                isSustainingChange = false
                timeOfFirstChange = 0
            }
        }
    }

    private fun activateSleepMode() {
        Log.d("SleepDetector", "Sleep detected! Stopping audio and waiting for shake.")
        isSleepModeActive = true

        // 1. Stop heart rate monitoring to save battery
        stopHeartRateMonitoring()

        // 2. Pause audio
        stopAudioPlayback()

        // 3. Start listening for the wrist shake
        startAccelerometerMonitoring()
    }

    private fun detectShake(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]

        // Calculate total acceleration magnitude minus gravity
        val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

        if (acceleration > shakeThreshold) {
            val currentTime = System.currentTimeMillis()
            // Prevent double-triggering within a 2-second window
            if (currentTime - lastShakeTime > 2000) {
                lastShakeTime = currentTime
                resumeAudioPlayback()
            }
        }
    }

    private fun stopAudioPlayback() {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()

        focusRequest?.let { audioManager.requestAudioFocus(it) }
        Log.d("SleepDetector", "Audio stopped.")
    }

    private fun resumeAudioPlayback() {
        Log.d("SleepDetector", "Shake detected! Resuming audio.")

        // 1. Abandon audio focus to let the media app resume
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }

        // 2. Reset flags to detect sleep again
        isSleepModeActive = false
        isSustainingChange = false
        readingsCount = 0 // Recalculate baseline in case their resting heart rate changed

        // 3. Swap sensors back to save battery
        stopAccelerometerMonitoring()
        startHeartRateMonitoring()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}