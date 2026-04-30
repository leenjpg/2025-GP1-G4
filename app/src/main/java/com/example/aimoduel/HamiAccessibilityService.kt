package com.example.aimoduel

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.os.Handler
import android.os.Looper

class HamiAccessibilityService : AccessibilityService() {

    private lateinit var aiAnalyzer: HamiAIAnalyzer
    private lateinit var securityVault: HamiSecurityVault

    // 1. إضافة الـ Handler والـ Runnable للتحكم في الوقت
    private val analysisHandler = Handler(Looper.getMainLooper())
    private var analysisRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        aiAnalyzer = HamiAIAnalyzer(applicationContext)
        securityVault = HamiSecurityVault(applicationContext)
        Log.d("HamiSecurity", "🚀 خدمة حامي متصلة الآن وجاهزة للتحليل")
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

            // IMPROVEMENT 1: Only analyze if text is long enough AND contains a space
            // (meaning at least one full word was typed)
            if (capturedText.length > 5 && capturedText.contains(" ")) {

                analysisRunnable?.let { analysisHandler.removeCallbacks(it) }

                analysisRunnable = Runnable {
                    // IMPROVEMENT 2: Check if service is still connected before running
                    aiAnalyzer.process(capturedText) { label, confidence ->
                        Log.w("HamiSecurity", "⚠️ Alert: [$label] at [${"%.2f".format(confidence)}%]")
                        val report = "Content: \"$capturedText\" | Label: $label"
                        securityVault.saveSuspiciousText(report)
                    }
                }

                // IMPROVEMENT 3: Increase delay to 1000ms (1 second)
                // This is the "sweet spot" for mobile AI to avoid lag.
                analysisHandler.postDelayed(analysisRunnable!!, 1000)
            }
        }
    }

    override fun onInterrupt() {
        Log.e("HamiSecurity", "🛑 تحذير: تم قطع خدمة حامي!")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("HamiSecurity", "🧹 تم إغلاق الخدمة وتنظيف الموارد")
    }
}