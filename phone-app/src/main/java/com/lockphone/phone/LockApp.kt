package com.lockphone.phone

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.IntentFilter

class LockApp : Application() {

    private val btReceiver = BluetoothStateReceiver()

    override fun onCreate() {
        super.onCreate()
        // Register at app process level — survives activity destruction
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(btReceiver, filter)
    }
}
