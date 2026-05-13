package com.lockphone.watch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val PREFS = "tile_prefs"
        private const val KEY_AUTO_LOCK = "auto_lock_enabled"
    }

    private lateinit var messageClient: MessageClient
    private var autoLockState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)

        // Load local state
        autoLockState.value = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_LOCK, true)

        // Start wrist detection service
        startService(Intent(this, WristDetectionService::class.java))

        setContent {
            LockPhoneScreen()
        }
    }

    override fun onDestroy() {
        messageClient.removeListener(this)
        super.onDestroy()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/lock-confirmed" -> {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
            "/auto-lock-state" -> {
                val enabled = messageEvent.data.isNotEmpty() && messageEvent.data[0] == 1.toByte()
                autoLockState.value = enabled
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_AUTO_LOCK, enabled).apply()
            }
        }
    }

    @Composable
    fun LockPhoneScreen() {
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf("Tap to lock phone") }
        val autoLock by autoLockState

        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                // Lock button
                Button(
                    onClick = {
                        scope.launch {
                            status = sendLockCommand()
                        }
                    },
                    modifier = Modifier.size(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF5C5C5C)
                    )
                ) {
                    Text("LOCK")
                }
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                Text(status, style = MaterialTheme.typography.caption3)
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                // Auto-lock toggle
                ToggleChip(
                    checked = autoLock,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            toggleAutoLock()
                        }
                    },
                    label = { Text("Auto-Lock") },
                    toggleControl = {
                        Switch(checked = autoLock)
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        }
    }

    private suspend fun sendLockCommand(): String {
        return try {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            if (nodes.isEmpty()) return "No phone connected"

            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    "/lock-phone",
                    byteArrayOf()
                ).await()
            }
            "Lock command sent!"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun toggleAutoLock() {
        try {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/toggle-auto-lock", byteArrayOf()).await()
            }
        } catch (_: Exception) {}
    }
}
