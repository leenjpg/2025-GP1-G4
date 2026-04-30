package com.example.aimoduel

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.firestore.FirebaseFirestore //for syncing-leen
import java.text.SimpleDateFormat
import java.util.*
import java.util.Date
import java.util.Locale

class HamiSecurityVault(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "hami_secure_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    ) // next is encrytpion function - leen
    fun saveSuspiciousText(text: String, riskType: String = "Pending", severity: String = "Unknown") {
        val messagesList = getAllMessages().toMutableList()

        val messageObject = JSONObject().apply { //asn entire objj
            put("text", text)
            put("riskType", riskType)
            put("severity", severity)
            put("timestamp", System.currentTimeMillis())
        }

        messagesList.add(messageObject)

        val jsonArray = JSONArray()
        messagesList.forEach { jsonArray.put(it) }

        sharedPreferences.edit().putString("all_messages", jsonArray.toString()).apply()
    }

    fun getAllMessages(): List<JSONObject> { //like set and get
        val jsonString = sharedPreferences.getString("all_messages", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val messages = mutableListOf<JSONObject>()

        for (i in 0 until jsonArray.length()) {
            messages.add(jsonArray.getJSONObject(i))
        }
        return messages
    }

    fun getHighRiskMessages(): List<JSONObject> { //filtering (later fixed w model)
        return getAllMessages().filter {
            it.optString("severity", "Unknown") == "High"
        }
    }

    fun clearAllMessages() { //testing
        sharedPreferences.edit().remove("all_messages").apply()
    }

    fun hasAnyThreats(): Boolean { //dbl check
        return getAllMessages().isNotEmpty()
    }
    fun getAlertsList(): List<AlertItem> {
        val alerts = mutableListOf<AlertItem>()
        val jsonString = sharedPreferences.getString("all_messages", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val riskType = obj.optString("riskType", "Pending")
            if (riskType != "Neutral") {
                alerts.add(
                    AlertItem(
                        text = obj.optString("text", ""),
                        riskLabel = riskType,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
        return alerts.sortedByDescending { it.timestamp }
    }

    fun deleteAlert(timestamp: Long) {
        val messagesList = getAllMessages().toMutableList()
        val iterator = messagesList.iterator()
        while (iterator.hasNext()) {
            val obj = iterator.next()
            if (obj.optLong("timestamp") == timestamp) {
                iterator.remove()
                break
            }
        }
        val jsonArray = JSONArray()
        messagesList.forEach { jsonArray.put(it) }
        sharedPreferences.edit().putString("all_messages", jsonArray.toString()).apply()
    }
    //  only for final alerts - leen
    fun saveAlert(alert: AlertItem) {
       // android.util.Log.d("HAMI_ENC", " SAVING ALERT: ${alert.riskLabel}") for testing-leen
        val messageObject = JSONObject().apply {
            put("text", alert.text)
            put("riskType", alert.riskLabel)
            put("severity", "Medium") // default for now
            put("timestamp", alert.timestamp)
            syncAlertToFirebase(alert) //firebase sync

        }

        val messagesList = getAllMessages().toMutableList()
        messagesList.add(messageObject)

        val jsonArray = JSONArray()
        messagesList.forEach { jsonArray.put(it) }
        sharedPreferences.edit().putString("all_messages", jsonArray.toString()).apply()
        // android.util.Log.d("HAMI_ENC", " ALERT ENCRYPTED AND STORED") for testing
    }
    fun syncAlertToFirebase(alert: AlertItem) {
        android.util.Log.d("FIREBASE_SYNC", " ATTEMPTING TO SYNC: ${alert.timestamp}")

        try {
            val db = FirebaseFirestore.getInstance() //syncing

            val alertData = hashMapOf(
                "text" to alert.text,
                "riskType" to alert.riskLabel,
                "severity" to "Medium",
                "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp)),
                "childId" to "default"
            )

            db.collection("alerts")
                .document(alert.timestamp.toString())
                .set(alertData)
                .addOnSuccessListener {
                    android.util.Log.d("FIREBASE_SYNC", " Alert synced to Firebase: ${alert.timestamp}")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("FIREBASE_SYNC", " Failed to sync: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.e("FIREBASE_SYNC", "Error: ${e.message}")
        }
    }
}