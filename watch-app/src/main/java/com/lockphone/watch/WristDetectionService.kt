package com.lockphone.watch

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class WristDetectionService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "WristDetect"
    }

    private lateinit var sensorManager: SensorManager
    private var offBodySensor: Sensor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Sensor type 34 = TYPE_LOW_LATENCY_OFFBODY_DETECT (Wear OS)
        offBodySensor = sensorManager.getDefaultSensor(34)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

        if (offBodySensor != null) {
            sensorManager.registerListener(this, offBodySensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Off-body sensor registered")
        } else {
            Log.e(TAG, "No off-body sensor available on this device")
        }
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        // value 0.0 = off wrist, 1.0 = on wrist
        if (event.values[0] == 0.0f) {
            Log.d(TAG, "Watch removed from wrist — sending lock command")
            serviceScope.launch {
                sendLockCommand()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private suspend fun sendLockCommand() {
        try {
            val nodes = Wearable.getNodeClient(this@WristDetectionService).connectedNodes.await()
            val messageClient = Wearable.getMessageClient(this@WristDetectionService)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/lock-phone", byteArrayOf()).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send lock: ${e.message}")
        }
    }
}
