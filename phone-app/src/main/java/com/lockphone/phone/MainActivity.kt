package com.lockphone.phone

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    companion object {
        private const val PREFS = "lock_prefs"
        private const val KEY_AUTO_LOCK = "auto_lock_enabled"
    }

    private val adminComponent by lazy {
        ComponentName(this, AdminReceiver::class.java)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private var authenticated = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()

        setContent {
            MaterialTheme {
                val isAuthenticated by authenticated
                if (isAuthenticated) {
                    MainScreen()
                } else {
                    BiometricLockScreen()
                }
            }
        }

        // Trigger biometric immediately
        authenticateUser()
    }

    private fun authenticateUser() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticated.value = true
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // If user cancels, close the app
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            finish()
                        }
                    }

                    override fun onAuthenticationFailed() {
                        // Stay on lock screen
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Lock Phone")
                .setSubtitle("Authenticate to access settings")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            prompt.authenticate(promptInfo)
        } else {
            // No biometric available, allow access
            authenticated.value = true
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
    fun BiometricLockScreen() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Authenticating...", style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    fun MainScreen() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        var isAdmin by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var autoLockEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_LOCK, true)) }

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
            Spacer(modifier = Modifier.height(24.dp))

            if (isAdmin) {
                Text("Device Admin is ACTIVE", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(24.dp))

                // Auto-lock toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-Lock on Disconnect", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Lock phone when watch disconnects",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoLockEnabled,
                        onCheckedChange = { enabled ->
                            autoLockEnabled = enabled
                            prefs.edit().putBoolean(KEY_AUTO_LOCK, enabled).apply()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Manual lock button
                Button(
                    onClick = {
                        dpm.lockNow()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("LOCK NOW", style = MaterialTheme.typography.titleMedium)
                }
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
