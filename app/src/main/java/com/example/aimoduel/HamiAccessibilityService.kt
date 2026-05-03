package com.example.aimoduel

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.util.Date
import android.content.Context

class HamiAccessibilityService : AccessibilityService() {

    private lateinit var aiAnalyzer: HamiAIAnalyzer
    private val db = FirebaseFirestore.getInstance()

    // IDs from SharedPreferences
    private var childId: String = "unknown"
    private var parentId: String = "unknown"

    private val analysisHandler = Handler(Looper.getMainLooper())
    private var analysisRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        aiAnalyzer = HamiAIAnalyzer(applicationContext)

        // Load IDs from SharedPreferences
        val sharedPref = applicationContext.getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        childId = sharedPref.getString("CHILD_ID", "unknown") ?: "unknown"
        parentId = sharedPref.getString("PARENT_ID", "unknown") ?: "unknown"

        // Update Firestore that service is enabled
        updateServiceStatus(true)

        Log.d("HamiSecurity", "✅ Service connected - Child: $childId, Parent: $parentId")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType

        // Only care about text changes
        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            var capturedText = event.text.joinToString(" ").trim()
            if (capturedText.isEmpty()) {
                capturedText = event.source?.text?.toString() ?: ""
            }

            // Only analyze if text is long enough and contains a space
            if (capturedText.length > 5 && capturedText.contains(" ")) {
                analysisRunnable?.let { analysisHandler.removeCallbacks(it) }

                analysisRunnable = Runnable {
                    // Process with AI
                    aiAnalyzer.process(capturedText) { label, confidence ->

                        Log.d("HamiSecurity", "📊 Analysis: $label with ${"%.1f".format(confidence * 100)}% confidence")

                        // ONLY save if it's dangerous (not Neutral) AND confidence > 60%
                        if (label != "Neutral" && confidence > 0.6) {

                            // Determine severity based on confidence
                            val severity = when {
                                confidence > 0.85 -> "high"
                                confidence > 0.7 -> "medium"
                                else -> "low"
                            }

                            Log.w("HamiSecurity", "⚠️ DANGEROUS CONTENT DETECTED!")
                            Log.w("HamiSecurity", "   Type: $label")
                            Log.w("HamiSecurity", "   Severity: $severity")
                            Log.w("HamiSecurity", "   Text: $capturedText")

                            // Save to Firestore
                            saveAlertToFirestore(
                                detectedText = capturedText,
                                type = label,
                                severity = severity,
                                confidence = confidence
                            )
                        } else {
                            Log.d("HamiSecurity", "✅ Content is safe (${label} with ${"%.1f".format(confidence * 100)}%)")
                        }
                    }
                }
                analysisHandler.postDelayed(analysisRunnable!!, 1000)
            }
        }
    }

    private fun saveAlertToFirestore(detectedText: String, type: String, severity: String, confidence: Float) {
        val vault = HamiSecurityVault(applicationContext)
        val alert = AlertItem(
            text = detectedText,
            riskLabel = type,
            timestamp = System.currentTimeMillis(),
            confidence = confidence,
            childId = childId,
            parentId = parentId,
            read = false,
            actionTaken = null,
            context = "keyboard_input"
        )
        vault.saveAlert(alert)
    }

    private fun updateServiceStatus(enabled: Boolean) {
        if (childId != "unknown") {
            db.collection("child").document(childId)
                .update("accessibilityServiceEnabled", enabled)
                .addOnSuccessListener {
                    Log.d("HamiSecurity", "✅ Service status updated to: $enabled")
                }
                .addOnFailureListener { e ->
                    Log.e("HamiSecurity", "Failed to update service status: ${e.message}")
                }
        }
    }

    override fun onInterrupt() {
        Log.e("HamiSecurity", "🛑 Service interrupted!")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Update Firestore that service is disabled
        updateServiceStatus(false)
        Log.d("HamiSecurity", "🧹 Service destroyed and cleaned up")
    }
}