package com.aviramj.codriver.car

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

/**
 * Posts the co:driver greeting automatically when the phone connects to the
 * car's Bluetooth, so a conversation is waiting in Android Auto without touching
 * the phone.
 *
 * ACL_CONNECTED is exempt from the implicit-broadcast limits, so this
 * manifest-registered (exported) receiver fires even when the app isn't running
 * — no foreground service needed. It is filtered to car audio / hands-free
 * devices so ordinary Bluetooth gear (headphones, watches) doesn't trigger a
 * greeting.
 */
class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val app = context.applicationContext

        // Reading a device's class needs BLUETOOTH_CONNECT on Android 12+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        if (!isCar(device)) return

        CarMessaging.startFromCarConnect(app)
    }

    private fun isCar(device: BluetoothDevice?): Boolean {
        val deviceClass = runCatching { device?.bluetoothClass?.deviceClass }.getOrNull() ?: return false
        return deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
    }
}
