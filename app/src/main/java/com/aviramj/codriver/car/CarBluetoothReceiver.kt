package com.aviramj.codriver.car

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService

/**
 * Auto-greets in Android Auto when the phone connects to the car's Bluetooth,
 * without a foreground service.
 *
 * ACL_CONNECTED is exempt from the implicit-broadcast limits, so this
 * manifest-registered (exported) receiver fires even when the app isn't running.
 * It is filtered to car audio / hands-free devices so ordinary Bluetooth gear
 * (headphones, watches) doesn't trigger a greeting.
 *
 * The greeting is posted GREET_DELAY_MS *after* the connection, via an inexact
 * AlarmManager alarm that re-wakes this receiver (so nothing has to stay alive in
 * between). The delay gives Android Auto time to finish establishing its session,
 * so the message lands "during the drive" instead of racing the connection
 * handshake.
 */
class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_DELAYED_GREET -> CarMessaging.startFromCarConnect(app)

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // Reading a device's class needs BLUETOOTH_CONNECT on Android 12+.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED
                ) return

                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (!isCar(device)) return

                scheduleDelayedGreet(app)
            }
        }
    }

    /**
     * Schedule the greeting for [GREET_DELAY_MS] later. The alarm re-delivers a
     * broadcast to this receiver, so no component has to stay alive in between.
     */
    private fun scheduleDelayedGreet(app: Context) {
        val alarm = app.getSystemService<AlarmManager>()
        if (alarm == null) {
            CarMessaging.startFromCarConnect(app)
            return
        }
        val intent = Intent(app, CarBluetoothReceiver::class.java).setAction(ACTION_DELAYED_GREET)
        val pi = PendingIntent.getBroadcast(
            app, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Inexact alarm: needs no special permission and is plenty precise for ~30s
        // while the phone is active (just connected to the car).
        alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + GREET_DELAY_MS, pi)
    }

    private fun isCar(device: BluetoothDevice?): Boolean {
        val deviceClass = runCatching { device?.bluetoothClass?.deviceClass }.getOrNull() ?: return false
        return deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
    }

    private companion object {
        const val ACTION_DELAYED_GREET = "com.aviramj.codriver.action.DELAYED_GREET"
        const val GREET_DELAY_MS = 30_000L
    }
}
