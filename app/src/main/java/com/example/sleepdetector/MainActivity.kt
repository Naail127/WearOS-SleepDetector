package com.example.sleepdetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val SENSOR_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            if (checkPermissions()) {
                startSleepService()
            } else {
                requestPermissions()
            }
        }

        btnStop.setOnClickListener {
            stopSleepService()
        }
    }

    private fun startSleepService() {
        val serviceIntent = Intent(this, SleepTrackingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Tracking Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopSleepService() {
        val serviceIntent = Intent(this, SleepTrackingService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Tracking Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        // Accelerometer does not require a dangerous permission request,
        // BODY_SENSORS covers the heart rate sensor.
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BODY_SENSORS),
            SENSOR_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SENSOR_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSleepService()
            } else {
                Toast.makeText(this, "Sensor permission required to track sleep.", Toast.LENGTH_LONG).show()
            }
        }
    }
}