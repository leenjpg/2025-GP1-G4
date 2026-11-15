package com.example.Hami.quiz.manager

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.example.Hami.quiz.QUIZ_LOG_TAG

/*
   The single entry point called by HamiAccessibilityService when a risky alert was saved.
   Everything is wrapped so the quiz can never break Hami's detection.
   Only the category label and alert id are used - never the message content.
 */
object QuizTriggerManager {

    /**
     * @param alertId   unique id of this alert (the alert's timestamp, same id used for its Firestore document)
     * @param timestamp the alert's own time (decides which calendar week it belongs to)
     */
    fun onRiskDetected(
        service: AccessibilityService,
        category: String,
        childId: String,
        alertId: String,
        timestamp: Long
    ) {
        try {
            if (childId.isBlank() || childId == "unknown") return
            if (QuizSelector.normalize(category) in setOf("neutral", "normal")) return // not an alert

            // Always count (even while a quiz is on screen) so no milestone can be lost
            val result = QuizAlertCounter(service.applicationContext)
                .registerAlert(childId, alertId, category, timestamp)
            Log.d(QUIZ_LOG_TAG, "Alert ($category) counted=${result.counted}, week total=${result.weeklyCount}, milestone=${result.newTrigger?.milestone}")

            QuizManager.showNextPending(service, childId)
        } catch (e: Exception) {
            Log.e(QUIZ_LOG_TAG, "Quiz trigger failed (Hami continues normally): ${e.message}")
        }
    }

    /** Called when the service (re)connects: restores remote history and shows any milestone quiz still owed. */
    fun onServiceStarted(service: AccessibilityService, childId: String) {
        try {
            if (childId.isBlank() || childId == "unknown") return
            QuizHistory(service.applicationContext).restoreFromRemote(childId)
            QuizManager.showNextPending(service, childId)
        } catch (e: Exception) {
            Log.e(QUIZ_LOG_TAG, "Quiz resume failed: ${e.message}")
        }
    }
}
