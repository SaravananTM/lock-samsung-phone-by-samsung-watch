package com.lockphone.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private lateinit var messageClient: MessageClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messageClient = Wearable.getMessageClient(this)

        setContent {
            LockPhoneScreen()
        }
    }

    @Composable
    fun LockPhoneScreen() {
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf("Tap to lock phone") }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        scope.launch {
                            status = sendLockCommand()
                        }
                    },
                    modifier = Modifier.size(80.dp)
                ) {
                    Text("LOCK")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.caption3)
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
}
