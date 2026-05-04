package com.example.Hami



import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context

class HamiAccessibilityService : AccessibilityService() {

    private lateinit var aiAnalyzer: AiEngine
    private val db = FirebaseFirestore.getInstance()

    // IDs from SharedPreferences
    private var childId: String = "unknown"
    private var parentId: String = "unknown"

    private var lastAnalyzedText: String = ""
    private val lastAlertTimes = mutableMapOf<String, Long>()
    private val COOLDOWN_TIME_MS = 3 * 60 * 1000L // 3 دقائق
    private val analysisHandler = Handler(Looper.getMainLooper())
    private var analysisRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        aiAnalyzer = AiEngine(applicationContext)

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
        var capturedText = ""
        var eventSourceContext = "unknown"


        when (eventType) {

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                capturedText = event.text.joinToString(" ").trim()
                if (capturedText.isEmpty()) {
                    capturedText = event.source?.text?.toString() ?: ""
                }
                eventSourceContext = "keyboard_input"
            }


            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notificationDetails = event.text
                if (notificationDetails.isNotEmpty()) {
                    capturedText = notificationDetails.joinToString(" ").trim()
                }
                eventSourceContext = "notification"
            }


            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {

                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    capturedText = extractTextFromNode(rootNode).trim()
                    eventSourceContext = "screen_reading"
                }
            }
        }

        if (capturedText.length > 5 && capturedText.contains(" ")) {


            if (capturedText == lastAnalyzedText) return
            lastAnalyzedText = capturedText

            analysisRunnable?.let { analysisHandler.removeCallbacks(it) }

            analysisRunnable = Runnable {
                // Process with AI
                aiAnalyzer.process(capturedText) { label, confidence ->

                    Log.d("HamiSecurity", "📊 Analysis: $label with ${"%.1f".format(confidence * 100)}% confidence")

                    if (label != "Neutral" && confidence > 0.6) {

                        val currentTime = System.currentTimeMillis()
                        val lastTimeThisLabelAlerted = lastAlertTimes[label] ?: 0L

                        // (التعديل الثاني) فحص التبريد: إذا مرت أقل من 3 دقايق على نفس التهديد، نتجاهل
                        if (currentTime - lastTimeThisLabelAlerted >= COOLDOWN_TIME_MS) {


                            lastAlertTimes[label] = currentTime

                            val severity = when {
                                confidence > 0.85 -> "high"
                                confidence > 0.7 -> "medium"
                                else -> "low"
                            }

                            Log.w("HamiSecurity", "⚠️ DANGEROUS CONTENT DETECTED!")
                            Log.w("HamiSecurity", "   Source: $eventSourceContext")
                            Log.w("HamiSecurity", "   Type: $label")
                            Log.w("HamiSecurity", "   Severity: $severity")

                            // Save to Firestore
                            saveAlertToFirestore(
                                detectedText = capturedText,
                                type = label,
                                severity = severity,
                                confidence = confidence,
                                sourceContext = eventSourceContext
                            )
                        } else {
                            Log.d("HamiSecurity", "⏳ تنبيه مكرر لنوع ($label)، تم إيقاف الإرسال لتجنب الإزعاج.")
                        }

                    } else {
                        Log.d("HamiSecurity", "✅ Content is safe (${label})")
                    }
                }
            }
            analysisHandler.postDelayed(analysisRunnable!!, 1500)
        }
    }


    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        var text = ""

        if (!node.text.isNullOrEmpty()) {
            text += node.text.toString() + " "
        } else if (!node.contentDescription.isNullOrEmpty()) {
            text += node.contentDescription.toString() + " "
        }

        for (i in 0 until node.childCount) {
            text += extractTextFromNode(node.getChild(i))
        }

        return text
    }


    private fun saveAlertToFirestore(detectedText: String, type: String, severity: String, confidence: Float, sourceContext: String) {
        val vault = HamiSecurityVault(applicationContext)
        val alert = AlertItem(
            text = detectedText,
            riskLabel = type,
            timestamp = System.currentTimeMillis(),
            confidence = confidence,
            childId = childId,
            parentId = parentId,
            read = false,
            context = sourceContext
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