package com.aviramj.codriver.car

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Observer
import com.aviramj.codriver.R

/**
 * Watches for the phone connecting to Android Auto and, when it does, posts a
 * fresh greeting so there is a message to reply to *during the drive*.
 *
 * Android Auto's messaging surface only shows messages that arrive while
 * connected and offers no "compose" button, so without this bootstrap the
 * conversation has no entry point (the car just shows "No new messages during
 * this drive"). Runs as a foreground service because the connection callback
 * only arrives while our process is alive.
 */
class CarChatService : Service() {

    private var carConnection: CarConnection? = null
    private var wasProjecting = false

    private val observer = Observer<Int> { type ->
        val projecting = type == CarConnection.CONNECTION_TYPE_PROJECTION
        if (projecting && !wasProjecting) {
            // Entered Android Auto: seed the conversation so it appears and can be replied to.
            CarMessaging.start(applicationContext)
        }
        wasProjecting = projecting
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        carConnection = CarConnection(this).also { it.type.observeForever(observer) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        carConnection?.type?.removeObserver(observer)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("co:driver is ready for Android Auto")
            .setContentText("Connect your car, then open co:driver under Messages.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FGS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FGS_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Car chat status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows that co:driver is ready to appear in Android Auto."
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "codriver_car_status"
        private const val FGS_ID = 7

        /** Start (from the foreground UI) the watcher that greets on car connect. */
        fun start(context: Context) {
            val intent = Intent(context, CarChatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
