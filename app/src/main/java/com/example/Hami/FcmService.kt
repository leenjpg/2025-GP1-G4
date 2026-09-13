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
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Get alert data from the message
        val alertId = message.data["alertId"] ?: return
        val type = message.data["type"] ?: "تنبيه"
        val childName = message.data["childName"] ?: "الطفل"
        val confidence = message.data["confidence"]?.toFloatOrNull() ?: 0.0f

        // Show notification with sound
        showNotification(
            title = getNotificationTitle(type, confidence),
            body = getNotificationBody(type, childName),
            alertId = alertId,
            type = type
        )

        // Vibrate the device
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "hami_alerts",
                "تنبيهات حامي",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فورية لحماية الطفل"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open Dashboard when notification is tapped
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

        // Build notification
        val notification = NotificationCompat.Builder(this, "hami_alerts")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, vibration, lights
            .setContentIntent(pendingIntent)
            .build()

        // Show notification with unique ID
        notificationManager.notify(alertId.hashCode(), notification)
    }

    private fun vibrateDevice() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            // Device might not have vibrator
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token to Firestore
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        val sharedPref = getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        val parentId = sharedPref.getString("PARENT_ID", null) ?: return

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("parent").document(parentId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                android.util.Log.d("FCM", "✅ Token saved for parent: $parentId")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FCM", "❌ Failed to save token: ${e.message}")
            }
    }
}