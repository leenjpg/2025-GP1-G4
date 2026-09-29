package com.example.Hami

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.RingtoneManager
import android.os.Build

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Pre-create the channel so background notifications land correctly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "hami_alerts",
                "تنبيهات حامي",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فورية لحماية الطفل"
                enableVibration(true)
                enableLights(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
            }
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }
}