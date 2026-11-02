package com.hayate.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Keeps the process alive while the browser is open during Google sign-in (loopback redirect). */
class SignInService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("signin", "Sign-in", NotificationManager.IMPORTANCE_LOW))
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "signin") else Notification.Builder(this)
        val n = b.setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Hayate").setContentText("Finishing Google sign-in…").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(7001, n)
        return START_NOT_STICKY
    }

    companion object {
        fun start(c: Context) {
            val i = Intent(c, SignInService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) { c.stopService(Intent(c, SignInService::class.java)) }
    }
}
