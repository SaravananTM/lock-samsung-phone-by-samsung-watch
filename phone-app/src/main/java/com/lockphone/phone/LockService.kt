package com.lockphone.phone

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class LockService : WearableListenerService() {

    companion object {
        private const val TAG = "LockService"
        private const val PREFS = "lock_prefs"
        private const val KEY_AUTO_LOCK = "auto_lock_enabled"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")
        when (messageEvent.path) {
            "/lock-phone" -> {
                lockPhone()
                // Send confirmation back to watch for haptic feedback
                sendConfirmation(messageEvent.sourceNodeId)
            }
            "/toggle-auto-lock" -> {
                val enabled = toggleAutoLock()
                // Send current state back to watch
                sendAutoLockState(messageEvent.sourceNodeId, enabled)
            }
            "/get-auto-lock-state" -> {
                val enabled = getAutoLockEnabled()
                sendAutoLockState(messageEvent.sourceNodeId, enabled)
            }
        }
    }

    override fun onPeerDisconnected(node: Node) {
        if (getAutoLockEnabled()) {
            Log.d(TAG, "Watch disconnected (peer lost: ${node.displayName}) — locking phone")
            lockPhone()
        } else {
            Log.d(TAG, "Watch disconnected but auto-lock is disabled")
        }
    }

    private fun lockPhone() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            Log.d(TAG, "Locking phone now")
            dpm.lockNow()
        } else {
            Log.e(TAG, "Device Admin is NOT active — cannot lock")
        }
    }

    private fun sendConfirmation(nodeId: String) {
        try {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, "/lock-confirmed", byteArrayOf())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send confirmation: ${e.message}")
        }
    }

    private fun sendAutoLockState(nodeId: String, enabled: Boolean) {
        try {
            val data = if (enabled) byteArrayOf(1) else byteArrayOf(0)
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, "/auto-lock-state", data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto-lock state: ${e.message}")
        }
    }

    private fun toggleAutoLock(): Boolean {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getBoolean(KEY_AUTO_LOCK, true)
        val newState = !current
        prefs.edit().putBoolean(KEY_AUTO_LOCK, newState).apply()
        Log.d(TAG, "Auto-lock toggled: $newState")
        return newState
    }

    private fun getAutoLockEnabled(): Boolean {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_LOCK, true)
    }
}
