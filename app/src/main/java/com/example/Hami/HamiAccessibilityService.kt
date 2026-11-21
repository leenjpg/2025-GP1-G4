package com.example.Hami

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context

// استدعاءات زميلاتك للكويز والقائمة الآمنة (مهمة عشان ما يخرب شغلهم)
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


    private val alertedWordsPool = mutableMapOf<String, Long>()


    private val safeWordsCache = mutableSetOf<String>()
    private var safeWordsLastLoadedAt = 0L
    private val SAFE_WORDS_REFRESH_MS = 5 * 60 * 1000L   // refresh every 5 min


    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    private val analysisHandler = Handler(Looper.getMainLooper())
    private var analysisRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        aiAnalyzer = AiEngine(applicationContext)

        val sharedPref = applicationContext.getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        childId = sharedPref.getString("CHILD_ID", "unknown") ?: "unknown"
        parentId = sharedPref.getString("PARENT_ID", "unknown") ?: "unknown"


        refreshSafeWordsIfNeeded(force = true)
        updateServiceStatus(true)

        if (sharedPref.getBoolean("CHILD_LOGGED_IN", false) &&
            sharedPref.getString("USER_ROLE", "") == "CHILD" && childId != "unknown") {
            activeQuizChildId = childId
            QuizTriggerManager.onServiceStarted(this, childId)
        }
        Log.d("HamiSecurity", "✅ Service connected - Child: $childId, Parent: $parentId")
    }


    private fun refreshSafeWordsIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - safeWordsLastLoadedAt < SAFE_WORDS_REFRESH_MS) return
        if (parentId == "unknown" || parentId.isBlank()) return

        safeWordsLastLoadedAt = now
        SafeWordsRepository.getSafeWords(parentId) { set ->
            if (!isActiveChildSession()) return@getSafeWords
            safeWordsCache.clear()
            safeWordsCache.addAll(set)
        }
    }

    private fun isWordSafe(word: String): Boolean {
        if (safeWordsCache.isEmpty()) return false
        return safeWordsCache.contains(SafeWordsRepository.normalizeWord(word))
    }

    private fun pickSafeCandidate(keywords: List<String>, newWordsList: List<String>): String {
        keywords.firstOrNull { it.isNotBlank() }?.let { return it }
        return newWordsList.firstOrNull { it.isNotBlank() } ?: ""
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = applicationContext.getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        val childLoggedIn = prefs.getBoolean("CHILD_LOGGED_IN", false) &&
                prefs.getString("USER_ROLE", "") == "CHILD"

        if (!childLoggedIn) {
            if (activeQuizChildId != null) QuizOverlayController.dismissForServiceStop()
            activeQuizChildId = null
            alertedWordsPool.clear()
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

        if (QuizManager.isQuizShowing) return

        var capturedText = ""
        var sourceContext = "unknown"

        val activePackage = rootInActiveWindow?.packageName?.toString()
        val packageName = activePackage ?: event.packageName?.toString() ?: ""

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

        if (sourceContext == "notification") {
            analyzeText(capturedText, sourceContext, appName, childId, parentId)
        } else {
            analysisRunnable?.let { analysisHandler.removeCallbacks(it) }
            val sessionChildId = childId
            val sessionParentId = parentId
            analysisRunnable = Runnable { analyzeText(capturedText, sourceContext, appName, sessionChildId, sessionParentId) }
            analysisHandler.postDelayed(analysisRunnable!!, 1500)
        }
    }


    private fun extractArabicWords(text: String): Set<String> {
        val stopWords = setOf(
            "من", "الى", "إلى", "عن", "على", "في", "انا", "أنا", "انت", "أنت", "انتي", "أنتي",
            "هو", "هي", "نحن", "هم", "هن", "هذا", "هذه", "ذلك", "تلك", "الذي", "التي",
            "يا", "لا", "ما", "هل", "كم", "كيف", "متى", "اين", "أين", "لو", "ان", "إن",
            "اذا", "إذا", "كان", "كانت", "وش", "اللي", "ياخي", "طيب", "بس"
        )
        return text.replace(Regex("[^\\u0600-\\u06FF]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 && !stopWords.contains(it) }
            .toSet()
    }


    private fun analyzeText(
        rawText: String,
        sourceContext: String,
        appName: String,
        sessionChildId: String = childId,
        sessionParentId: String = parentId
    ) {
        if (!isActiveChildSession() || childId != sessionChildId || parentId != sessionParentId) return
        refreshSafeWordsIfNeeded()

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
            return
        }

        aiAnalyzer.process(textToAnalyze) { label, confidence ->
            if (!isActiveChildSession() || childId != sessionChildId || parentId != sessionParentId) return@process

            if (!label.equals("Neutral", ignoreCase = true) &&
                !label.equals("Normal", ignoreCase = true) && confidence > 0.6) {

                val severity = when {
                    confidence > 0.85 -> "high"
                    confidence > 0.7 -> "medium"
                    else -> "low"
                }

                for (word in newWordsList) {
                    alertedWordsPool[word] = System.currentTimeMillis()
                }

                val offensiveKeywords = mutableListOf<String>()
                var wordsChecked = 0


                if (newWordsList.size == 1) {
                    val candidate = newWordsList.first()
                    if (isWordSafe(candidate)) {
                        return@process
                    }
                    saveAlertToFirestore(
                        detectedText = candidate,
                        type = label,
                        severity = severity,
                        confidence = confidence,
                        sourceContext = sourceContext,
                        keywords = newWordsList,
                        appName = appName,
                        detectedWord = candidate
                    )
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

                        if (wordsChecked == newWordsList.size) {
                            val finalKeywords = if (offensiveKeywords.isNotEmpty()) offensiveKeywords else emptyList()
                            val candidate = pickSafeCandidate(finalKeywords, newWordsList)

                            if (candidate.isNotBlank() && isWordSafe(candidate)) {
                                return@process
                            }
                            saveAlertToFirestore(
                                detectedText = candidate,
                                type = label,
                                severity = severity,
                                confidence = confidence,
                                sourceContext = sourceContext,
                                keywords = finalKeywords,
                                appName = appName,
                                detectedWord = candidate
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


    private fun isActiveChildSession(): Boolean {
        val prefs = applicationContext.getSharedPreferences("HamiPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("CHILD_LOGGED_IN", false) &&
                prefs.getString("USER_ROLE", "") == "CHILD" &&
                prefs.getString("CHILD_ID", "") == childId &&
                prefs.getString("PARENT_ID", "") == parentId
    }


    private fun saveAlertToFirestore(detectedText: String, type: String, severity: String, confidence: Float, sourceContext: String, keywords: List<String>, appName: String, detectedWord: String = "") {
        if (!isActiveChildSession() || childId == "unknown" || parentId == "unknown") return

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
            appName = appName,
            detectedWord = detectedWord
        )
        try {
            vault.saveAlert(alert)
        } catch (e: Exception) {
            Log.e("HamiSecurity", "Failed to save alert", e)
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
        QuizOverlayController.dismissForServiceStop()
        super.onDestroy()
        updateServiceStatus(false)
        Log.d("HamiSecurity", "🧹 Service destroyed and cleaned up")
    }
}