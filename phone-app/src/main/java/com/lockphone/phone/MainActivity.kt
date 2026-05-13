package com.lockphone.phone

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val adminComponent by lazy {
        ComponentName(this, AdminReceiver::class.java)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()

        setContent {
            MaterialTheme {
                SetupScreen()
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    @Composable
    fun SetupScreen() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        var isAdmin by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Lock Phone from Watch",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isAdmin) {
                Text("Device Admin is ACTIVE.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Your phone will lock automatically when the watch disconnects or Bluetooth is turned off.")
            } else {
                Text("You need to grant Device Admin permission for this app to lock the screen.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Required to lock the screen from your Galaxy Watch"
                        )
                    }
                    startActivity(intent)
                }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
