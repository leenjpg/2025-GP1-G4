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

    private var childId: String = "unknown"
    private var parentId: String = "unknown"

    // (لمنع التكرار الذكي)
    private val alertedWordsPool = mutableMapOf<String, Long>()
    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    private val analysisHandler = Handler(Looper.getMainLooper())
    private var analysisRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        aiAnalyzer = AiEngine(applicationContext)

        // جلب الـ IDs من الذاكرة
        val sharedPref = applicationContext.getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        childId = sharedPref.getString("CHILD_ID", "unknown") ?: "unknown"
        parentId = sharedPref.getString("PARENT_ID", "unknown") ?: "unknown"

        updateServiceStatus(true)
        Log.d("HamiSecurity", "✅ Service connected - Child: $childId, Parent: $parentId")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // إذا ما فيه حدث أو الـ IDs لسه unknown، لا تسوي شيء
        if (event == null || childId == "unknown") return

        var capturedText = ""
        var sourceContext = "unknown"

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notificationDetails = event.text
                if (notificationDetails.isNotEmpty()) {
                    capturedText = notificationDetails.joinToString(" ").trim()
                }
                sourceContext = "notification"
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                capturedText = event.text.joinToString(" ").trim()
                sourceContext = "keyboard_input"
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    capturedText = extractTextFromNode(rootNode).trim()
                    sourceContext = "screen_reading"
                }
            }
        }


        if (capturedText.length < 3) return


        if (sourceContext == "notification") {
            analyzeText(capturedText, sourceContext)
        } else {

            analysisRunnable?.let { analysisHandler.removeCallbacks(it) }
            analysisRunnable = Runnable { analyzeText(capturedText, sourceContext) }
            analysisHandler.postDelayed(analysisRunnable!!, 1500)
        }
    }


    private fun extractArabicWords(text: String): Set<String> {
        return text.replace(Regex("[^\\u0600-\\u06FF]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .toSet()
    }

    private fun analyzeText(rawText: String, sourceContext: String) {
        val allArabicWords = extractArabicWords(rawText)
        if (allArabicWords.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val newWordsList = mutableListOf<String>()


        for (word in allArabicWords) {
            val savedTime = alertedWordsPool[word]
            if (savedTime != null) {
                if (currentTime - savedTime >= ONE_DAY_MS) {
                    alertedWordsPool.remove(word)
                    newWordsList.add(word)
                }
            } else {
                newWordsList.add(word) // كلمة جديدة كلياً
            }
        }


        val textToAnalyze = newWordsList.joinToString(" ")


        if (textToAnalyze.isBlank()) {
            Log.d("HamiSecurity", "⏳ تم الصد: النص موجود مسبقاً في الذاكرة كإشعار أو شاشة.")
            return
        }


        aiAnalyzer.process(textToAnalyze) { label, confidence ->
            if (label != "Neutral" && confidence > 0.6) {

                // 4. الحجز المسبق: بما أن الكلمات الجديدة هذي سببت تنبيه، نحفظها في الذاكرة لمدة 24 ساعة
                for (word in newWordsList) {
                    alertedWordsPool[word] = System.currentTimeMillis()
                }

                val severity = when {
                    confidence > 0.85 -> "high"
                    confidence > 0.7 -> "medium"
                    else -> "low"
                }

                Log.w("HamiSecurity", "⚠️ ALERT: $label detected via $sourceContext")


                saveAlertToFirestore(rawText, label, severity, confidence, sourceContext)
            }
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
        updateServiceStatus(false)
        Log.d("HamiSecurity", "🧹 Service destroyed and cleaned up")
    }
}
