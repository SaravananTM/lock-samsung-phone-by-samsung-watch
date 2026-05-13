package com.lockphone.phone

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class LockService : WearableListenerService() {

    companion object {
        private const val TAG = "LockService"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")

        if (messageEvent.path == "/lock-phone") {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, AdminReceiver::class.java)

            if (dpm.isAdminActive(adminComponent)) {
                Log.d(TAG, "Locking phone now")
                dpm.lockNow()
            } else {
                Log.e(TAG, "Device Admin is NOT active — cannot lock")
            }
        }
    }
}
