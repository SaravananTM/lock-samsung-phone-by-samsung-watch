package com.lockphone.phone

import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BTReceiver"
        private const val PREFS = "lock_prefs"
        private const val KEY_AUTO_LOCK = "auto_lock_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        if (state == BluetoothAdapter.STATE_OFF) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val autoLockEnabled = prefs.getBoolean(KEY_AUTO_LOCK, true)

            if (autoLockEnabled) {
                Log.d(TAG, "Bluetooth off + auto-lock enabled — locking phone")
                lockPhone(context)
            } else {
                Log.d(TAG, "Bluetooth off but auto-lock disabled — skipping")
            }
        }
    }

    private fun lockPhone(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, AdminReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            dpm.lockNow()
        } else {
            Log.e(TAG, "Device Admin not active")
        }
    }
}
