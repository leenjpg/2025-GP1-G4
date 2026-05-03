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
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class HamiSecurityVault(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val masterKeyAlias = MasterKey.DEFAULT_MASTER_KEY_ALIAS //forfirebase fucntion

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
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        confidence = obj.optDouble("confidence", 0.0).toFloat(),
                        childId = obj.optString("childId", "default"),
                        parentId = obj.optString("parentId", "unknown"),
                        read = obj.optBoolean("read", false),
                        actionTaken = obj.optString("actionTaken", null),
                        context = obj.optString("context", "keyboard_input")
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
        val messageObject = JSONObject().apply {
            put("text", alert.text)
            put("riskType", alert.riskLabel)
            put("severity", "High")
            put("timestamp", alert.timestamp)
            put("childId", alert.childId)
            put("parentId", alert.parentId)
            put("confidence", alert.confidence)
        }

        syncAlertToFirebase(alert)

        val messagesList = getAllMessages().toMutableList()
        messagesList.add(messageObject)

        val jsonArray = JSONArray()
        messagesList.forEach { jsonArray.put(it) }
        sharedPreferences.edit().putString("all_messages", jsonArray.toString()).apply()
    }

    fun syncAlertToFirebase(alert: AlertItem) {
        android.util.Log.d("FIREBASE_SYNC", " ATTEMPTING TO SYNC: ${alert.timestamp}")

        try {
            val db = FirebaseFirestore.getInstance()
            val encryptedText = encryptAES256(alert.text)

            val alertData = hashMapOf(
                "text" to encryptedText,
                "type" to alert.riskLabel.lowercase(),
                "severity" to "high",
                "timestamp" to com.google.firebase.Timestamp(Date(alert.timestamp)),
                "childId" to alert.childId,
                "parentId" to alert.parentId,
                "confidence" to alert.confidence,
                "context" to alert.context,
                "read" to alert.read,
                "actionTaken" to alert.actionTaken,
                // "detectedText" to encryptedText  // enc -leen
            )

            db.collection("alert")
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
    private fun encryptAES256(plainText: String): String { // for firebase sync-encytped - diff than salmas enc
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }
            val secretKey = keyStore.getKey(masterKeyAlias, null) as SecretKey

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray())
            val combined = iv + encryptedBytes
            android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            android.util.Log.e("AES", "Encryption failed: ${e.message}")
            plainText
        }
    }
    fun decryptAES256(encryptedText: String): String {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }
            val secretKey = keyStore.getKey(masterKeyAlias, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val decoded = android.util.Base64.decode(encryptedText, android.util.Base64.DEFAULT)
            val iv = decoded.copyOfRange(0, 12)
            val encrypted = decoded.copyOfRange(12, decoded.size)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val decryptedBytes = cipher.doFinal(encrypted)
            String(decryptedBytes)
        } catch (e: Exception) {
            android.util.Log.e("AES", "Decryption failed: ${e.message}")
            encryptedText
        }
    }
}