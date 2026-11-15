package com.example.Hami.quiz.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.Hami.quiz.QUIZ_LOG_TAG
import com.example.Hami.quiz.model.Quiz

/*
   Shows the quiz as a floating window above the child's current app.

   Why an accessibility overlay: Hami already runs as an AccessibilityService, and Android lets an
   accessibility service add TYPE_ACCESSIBILITY_OVERLAY windows WITHOUT any extra permission
   (no "Display over other apps" / SYSTEM_ALERT_WINDOW needed).

   The window is a dimmed scrim holding a medium-sized card. There is no close/skip button and the
   Back key is consumed. It only goes away when the child finishes the quiz (or the service stops).
   Home/Recents are system-owned and are not (and must not be) blocked.
 */
object QuizOverlayController {

    private const val ATTACH_GRACE_MS = 3000L

    private var rootView: QuizRootLayout? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var shownAt = 0L
    private var finishing = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isShowing(): Boolean {
        val view = rootView ?: return false
        // Self-heal: if the system removed our window (e.g. service restarted), don't stay "showing" forever
        if (!view.isAttachedToWindow && SystemClock.elapsedRealtime() - shownAt > ATTACH_GRACE_MS) {
            Log.w(QUIZ_LOG_TAG, "Overlay window no longer attached - clearing stale state")
            cleanup()
            return false
        }
        return true
    }

    /** Returns true if the overlay window was added. [onFinished] runs after the child taps "إنهاء". */
    fun show(
        service: AccessibilityService,
        quiz: Quiz,
        onQuestionShown: (String) -> Unit = {},
        onFinished: () -> Unit
    ): Boolean {
        if (isShowing()) return false

        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val owner = OverlayLifecycleOwner().also { it.start() }

        val root = QuizRootLayout(service).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }
        val compose = ComposeView(service).apply {
            setContent {
                QuizOverlayContent(
                    quiz = quiz,
                    onFinish = { finish(onFinished) },
                    onQuestionShown = onQuestionShown
                )
            }
        }
        root.addView(
            compose,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // focusable on purpose: lets us consume Back
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        return try {
            wm.addView(root, params)
            rootView = root
            windowManager = wm
            lifecycleOwner = owner
            shownAt = SystemClock.elapsedRealtime()
            finishing = false
            true
        } catch (e: Exception) {
            Log.e(QUIZ_LOG_TAG, "addView failed: ${e.message}")
            try { owner.stop() } catch (_: Exception) {}
            false
        }
    }

    private fun finish(onFinished: () -> Unit) {
        if (rootView == null || finishing) return
        finishing = true
        // Post so we don't tear the window down from inside its own click handler
        mainHandler.post {
            cleanup()
            try { onFinished() } catch (e: Exception) {
                Log.e(QUIZ_LOG_TAG, "onFinished failed: ${e.message}")
            }
        }
    }

    private fun cleanup() {
        val view = rootView
        val wm = windowManager
        val owner = lifecycleOwner
        rootView = null
        windowManager = null
        lifecycleOwner = null
        finishing = false
        try { if (view != null) wm?.removeViewImmediate(view) } catch (e: Exception) {
            Log.e(QUIZ_LOG_TAG, "removeView failed: ${e.message}")
        }
        try { owner?.stop() } catch (e: Exception) {
            Log.e(QUIZ_LOG_TAG, "lifecycle stop failed: ${e.message}")
        }
    }
    fun dismissForServiceStop() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup()
        } else {
            mainHandler.post {
                cleanup()
            }
        }
    }

    /** Root of the window: swallows the Back key so the quiz can't be dismissed with it. */
    private class QuizRootLayout(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
            return super.dispatchKeyEvent(event)
        }
    }

    /** Compose needs a lifecycle + saved-state owner; a Service window has none, so we provide one. */
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun start() {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
