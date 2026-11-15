package com.example.Hami

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context
import com.example.Hami.quiz.manager.QuizManager
import com.example.Hami.quiz.manager.QuizTriggerManager
import com.example.Hami.quiz.ui.QuizOverlayController

class HamiAccessibilityService : AccessibilityService() {

    private lateinit var aiAnalyzer: AiEngine
    private val db = FirebaseFirestore.getInstance()
    private var activeQuizChildId: String? = null
    private var lastAlertTimestamp: Long = 0L
    private var childId: String = "unknown"
    private var parentId: String = "unknown"

    // (لمنع التكرار الذكي)
    private val alertedWordsPool = mutableMapOf<String, Long>()
    // تم إصلاح علامات الضرب هنا
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
        // Only resume a quiz on an authenticated child's device.
        if (sharedPref.getBoolean("CHILD_LOGGED_IN", false) &&
            sharedPref.getString("USER_ROLE", "") == "CHILD" && childId != "unknown") {
            activeQuizChildId = childId
            QuizTriggerManager.onServiceStarted(this, childId)
        }
        Log.d("HamiSecurity", "✅ Service connected - Child: $childId, Parent: $parentId")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Re-check the role on every event. The accessibility service can remain enabled
        // while parent/child accounts change, so never analyze the parent's screen.
        val prefs = applicationContext.getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        val childLoggedIn = prefs.getBoolean("CHILD_LOGGED_IN", false) &&
                prefs.getString("USER_ROLE", "") == "CHILD"
        if (!childLoggedIn) {
            if (activeQuizChildId != null) QuizOverlayController.dismissForServiceStop()
            activeQuizChildId = null
            alertedWordsPool.clear() // Do not carry the previous child's deduplication history.
            analysisRunnable?.let { analysisHandler.removeCallbacks(it) }
            analysisRunnable = null
            return
        }
        childId = prefs.getString("CHILD_ID", "unknown") ?: "unknown"
        parentId = prefs.getString("PARENT_ID", "unknown") ?: "unknown"
        if (event == null || childId == "unknown" || parentId == "unknown") return

        if (activeQuizChildId != childId) {
            if (activeQuizChildId != null) QuizOverlayController.dismissForServiceStop()
            analysisRunnable?.let { analysisHandler.removeCallbacks(it) }
            analysisRunnable = null
            alertedWordsPool.clear()
            activeQuizChildId = childId
            QuizTriggerManager.onServiceStarted(this, childId)
        }
        // The child's own learning overlay must not generate harmful-language alerts.
        if (QuizManager.isQuizShowing) return

        var capturedText = ""
        var sourceContext = "unknown"

        // 1. استخراج اسم الحزمة وتحويله لاسم تطبيق مقروء
        val activePackage = rootInActiveWindow?.packageName?.toString()
        val packageName = activePackage ?: event.packageName?.toString() ?: ""

        Log.d("HamiSecurity", "Current Package Name: $packageName")

        val appName = when {
            packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            packageName.contains("instagram", ignoreCase = true) -> "Instagram"
            packageName.contains("snapchat", ignoreCase = true) -> "Snapchat"
            packageName.contains("tiktok", ignoreCase = true) || packageName.contains("musically", ignoreCase = true) -> "TikTok"
            packageName.contains("roblox", ignoreCase = true) -> "Roblox"
            packageName.contains("youtube", ignoreCase = true) -> "YouTube"
            packageName.contains("messaging", ignoreCase = true) || packageName.contains("message", ignoreCase = true) || packageName.contains("mms", ignoreCase = true) -> "Messages"
            else -> "تطبيق آخر"
        }

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

        // 2. تمرير اسم التطبيق (appName) لدالة التحليل
        if (sourceContext == "notification") {
            analyzeText(capturedText, sourceContext, appName, childId, parentId)
        } else {
            analysisRunnable?.let { analysisHandler.removeCallbacks(it) }
            val sessionChildId = childId
            val sessionParentId = parentId
            analysisRunnable = Runnable {
                analyzeText(capturedText, sourceContext, appName, sessionChildId, sessionParentId)
            }
            analysisHandler.postDelayed(analysisRunnable!!, 1500)
        }
    }

    private fun extractArabicWords(text: String): Set<String> {
        // قائمة بكلمات التوقف العربية الشائعة (الضمائر، حروف الجر، أدوات الاستفهام)
        val stopWords = setOf(
            "من", "الى", "إلى", "عن", "على", "في", "انا", "أنا", "انت", "أنت", "انتي", "أنتي",
            "هو", "هي", "نحن", "هم", "هن", "هذا", "هذه", "ذلك", "تلك", "الذي", "التي",
            "يا", "لا", "ما", "هل", "كم", "كيف", "متى", "اين", "أين", "لو", "ان", "إن",
            "اذا", "إذا", "كان", "كانت", "وش", "اللي", "ياخي", "طيب", "بس"
        )

        return text.replace(Regex("[^\\u0600-\\u06FF]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            // نأخذ الكلمة فقط إذا كان طولها أكثر من حرفين، ولا توجد في قائمة كلمات التوقف
            .filter { it.length > 2 && !stopWords.contains(it) }
            .toSet()
    }

    // تم إضافة appName هنا
    private fun analyzeText(
        rawText: String,
        sourceContext: String,
        appName: String,
        sessionChildId: String = childId,
        sessionParentId: String = parentId
    ) {
        // Ignore delayed callbacks after logout or when switching children.
        if (!isActiveChildSession() || childId != sessionChildId || parentId != sessionParentId) return
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
                newWordsList.add(word)
            }
        }

        val textToAnalyze = newWordsList.joinToString(" ")

        if (textToAnalyze.isBlank()) {
            Log.d("HamiSecurity", "⏳ تم الصد: النص موجود مسبقاً في الذاكرة كإشعار أو شاشة.")
            return
        }

        aiAnalyzer.process(textToAnalyze) { label, confidence ->
            if (!isActiveChildSession() || childId != sessionChildId || parentId != sessionParentId) return@process
            if (!label.equals("Neutral", ignoreCase = true) &&
                !label.equals("Normal", ignoreCase = true) && confidence > 0.6) {

                // 1. تعريف الـ severity هنا أول شيء عشان يختفي الخطأ الأحمر
                val severity = when {
                    confidence > 0.85 -> "high"
                    confidence > 0.7 -> "medium"
                    else -> "low"
                }

                // 2. تحديث الذاكرة لمنع التكرار
                for (word in newWordsList) {
                    alertedWordsPool[word] = System.currentTimeMillis()
                }

                Log.w("HamiSecurity", "⚠️ ALERT: $label detected via $sourceContext in $appName")

                // 3. فلترة الكلمات لمعرفة الشتيمة
                val offensiveKeywords = mutableListOf<String>()
                var wordsChecked = 0

                if (newWordsList.size == 1) {
                    saveAlertToFirestore(rawText, label, severity, confidence, sourceContext, newWordsList, appName)
                    return@process
                }

                for (word in newWordsList) {
                    aiAnalyzer.process(word) { wordLabel, _ ->
                        if (!isActiveChildSession() || childId != sessionChildId || parentId != sessionParentId) return@process
                        if (!wordLabel.equals("Neutral", ignoreCase = true) &&
                            !wordLabel.equals("Normal", ignoreCase = true)) {
                            offensiveKeywords.add(word)
                        }
                        wordsChecked++

                        // 4. الحفظ النهائي بعد فحص كل الكلمات
                        if (wordsChecked == newWordsList.size) {
                            // كحل احتياطي: لو المودل ما لقط الشتيمة المفردة، نرسل قائمة فارغة للداشبورد عشان ما تتلوث الاحصائية
                            val finalKeywords = if (offensiveKeywords.isNotEmpty()) offensiveKeywords else emptyList()

                            saveAlertToFirestore(
                                rawText, label, severity, confidence,
                                sourceContext, finalKeywords, appName
                            )
                        }
                    }
                }
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

    // Counts an alert only after the original secure vault successfully accepted it.
    // The quiz receives just the category, timestamp and child's ID – not message text.
    private fun isActiveChildSession(): Boolean {
        val prefs = applicationContext.getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("CHILD_LOGGED_IN", false) &&
                prefs.getString("USER_ROLE", "") == "CHILD" &&
                prefs.getString("CHILD_ID", "") == childId &&
                prefs.getString("PARENT_ID", "") == parentId
    }

    private fun saveAlertToFirestore(detectedText: String, type: String, severity: String, confidence: Float, sourceContext: String, keywords: List<String>, appName: String) {
        if (!isActiveChildSession() || childId == "unknown" || parentId == "unknown") return
        // Firestore alert document ID is the timestamp, so make it unique even if two
        // detections complete in the same millisecond on the main thread.
        val timestamp = maxOf(System.currentTimeMillis(), lastAlertTimestamp + 1)
        lastAlertTimestamp = timestamp
        val vault = HamiSecurityVault(applicationContext)
        val alert = AlertItem(
            text = detectedText,
            riskLabel = type,
            timestamp = timestamp,
            confidence = confidence,
            childId = childId,
            parentId = parentId,
            read = false,
            context = sourceContext,
            keywords = keywords,
            appName = appName // تم ربط اسم التطبيق
        )
        try {
            vault.saveAlert(alert)
        } catch (e: Exception) {
            Log.e("HamiSecurity", "Failed to save alert; quiz will not be counted", e)
            return
        }
        QuizTriggerManager.onRiskDetected(
            service = this,
            category = type,
            childId = alert.childId,
            alertId = timestamp.toString(),
            timestamp = timestamp
        )
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
        analysisRunnable?.let { analysisHandler.removeCallbacks(it) }
        analysisRunnable = null
        // A stopped service cannot keep its overlay. The unfinished weekly trigger
        // remains queued so it can be shown again when the service reconnects.
        QuizOverlayController.dismissForServiceStop()
        super.onDestroy()
        updateServiceStatus(false)
        Log.d("HamiSecurity", "🧹 Service destroyed and cleaned up")
    }
}