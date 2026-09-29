package com.example.Hami

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "HamiFCM"
        private const val CHANNEL_ID = "hami_alerts"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "📩 onMessageReceived called!")
        Log.d(TAG, "   data: ${message.data}")
        Log.d(TAG, "   notification: ${message.notification?.title} / ${message.notification?.body}")

        // If a notification payload exists, the system already displayed it.
        // We still run our custom logic for sound/vibration/data.
        val alertId = message.data["alertId"] ?: System.currentTimeMillis().toString()
        val type = message.data["type"]
            ?: message.notification?.title
            ?: "تنبيه"
        val childName = message.data["childName"] ?: "الطفل"
        val confidence = message.data["confidence"]?.toFloatOrNull() ?: 0.0f

        Log.d(TAG, "   parsed → alertId=$alertId, type=$type, child=$childName, conf=$confidence")

        showNotification(
            title = getNotificationTitle(type, confidence),
            body = getNotificationBody(type, childName),
            alertId = alertId,
            type = type
        )

        vibrateDevice()
    }

    private fun getNotificationTitle(type: String, confidence: Float): String {
        return when {
            confidence > 0.85 -> "🚨 تنبيه عالي الخطورة"
            confidence > 0.7 -> "⚠️ تنبيه مهم"
            else -> "📢 تنبيه حماية"
        }
    }

    private fun getNotificationBody(type: String, childName: String): String {
        val typeArabic = when (type.lowercase()) {
            "offensive" -> "محتوى هجومي"
            "sexism" -> "عنصرية جنسية"
            "religious discrimination" -> "تعصب ديني"
            "racism" -> "عنصرية عرقية"
            else -> type
        }
        return "تم اكتشاف $typeArabic من $childName"
    }

    private fun showNotification(title: String, body: String, alertId: String, type: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (safe to call every time; it's a no-op if it exists)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
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
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("alertId", alertId)
            putExtra("alertType", type)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(alertId.hashCode(), notification)
        Log.d(TAG, "✅ Notification shown: $title — $body")
    }

    private fun vibrateDevice() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                mgr.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔑 New FCM token: $token")
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        val sharedPref = getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        val parentId = sharedPref.getString("PARENT_ID", null) ?: run {
            Log.w(TAG, "No PARENT_ID in prefs, cannot save token")
            return
        }

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("parent").document(parentId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Token saved for parent: $parentId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save token: ${e.message}")
            }
    }
}