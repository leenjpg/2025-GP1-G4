package com.example.Hami.quiz.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.Hami.quiz.QUIZ_LOG_TAG
import java.util.Locale

/*
   On-demand Arabic read-aloud for quiz questions.

   Deliberately isolated from the quiz logic: nothing here knows about answers, scoring, progress or
   navigation. The UI only calls speak(text) when the child taps the button, and stop().

   Uses Android's built-in TextToSpeech engine: no extra dependency, no network service, no API key.
   Whether an Arabic voice exists (or needs a download) depends on the device, so every failure path
   degrades to a small hint message and never blocks answering the question.
 */

enum class SpeechStatus { Idle, Speaking, NotReady, Unavailable }

enum class SpeakResult { Started, NotReady, Unavailable }

/** Thin seam over the platform engine so the controller's logic can be unit-tested with a fake. */
interface SpeechEngine {
    /** Starts speaking [text], replacing anything that is currently playing. */
    fun speak(text: String, utteranceId: String): SpeakResult
    fun stop()
    fun release()
}

/**
 * Tracks which utterance is "current" so that late callbacks from an old/cancelled utterance can never
 * change the status of a newer one (no overlapping speech, no stale state).
 */
class QuizSpeechController(
    private val engine: SpeechEngine,
    private val onStatus: (SpeechStatus) -> Unit
) {
    private var counter = 0
    private var activeId: String? = null
    private var released = false

    fun speak(text: String) {
        if (released || text.isBlank()) return
        val id = "quiz-speech-${++counter}"
        activeId = id // set first: the engine flushes any previous utterance, whose callbacks are now stale
        when (engine.speak(text, id)) {
            SpeakResult.Started -> onStatus(SpeechStatus.Speaking)
            SpeakResult.NotReady -> { activeId = null; onStatus(SpeechStatus.NotReady) }
            SpeakResult.Unavailable -> { activeId = null; onStatus(SpeechStatus.Unavailable) }
        }
    }

    fun stop() {
        if (released) return
        activeId = null
        engine.stop()
        onStatus(SpeechStatus.Idle)
    }

    fun onUtteranceFinished(id: String) {
        if (released || id != activeId) return
        activeId = null
        onStatus(SpeechStatus.Idle)
    }

    fun onUtteranceFailed(id: String) {
        if (released || id != activeId) return
        activeId = null
        onStatus(SpeechStatus.Unavailable)
    }

    fun release() {
        if (released) return
        released = true
        activeId = null
        engine.release()
    }
}

/** Android TextToSpeech wrapper. Picks an Arabic locale only if the device actually supports one. */
class AndroidSpeechEngine(context: Context, private val callbacks: Callbacks) : SpeechEngine {

    interface Callbacks {
        fun onFinished(utteranceId: String)
        fun onFailed(utteranceId: String)
    }

    private enum class EngineState { Initializing, ArabicReady, NoArabic }

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var state = EngineState.Initializing
    private var released = false

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) = finished(utteranceId)

        // onStop fires when we cancel/replace an utterance; the controller ignores stale ids
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = failed(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = failed(utteranceId)

        // TTS callbacks arrive on a binder thread; hop to the main thread before touching UI state
        private fun finished(id: String?) {
            if (id != null) main.post { if (!released) callbacks.onFinished(id) }
        }

        private fun failed(id: String?) {
            if (id != null) main.post { if (!released) callbacks.onFailed(id) }
        }
    }

    init {
        tts = try {
            TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }
        } catch (e: Exception) {
            Log.w(QUIZ_LOG_TAG, "TextToSpeech unavailable: ${e.message}")
            state = EngineState.NoArabic
            null
        }
    }

    private fun onInit(status: Int) {
        val engine = tts
        if (released || engine == null) return
        if (status != TextToSpeech.SUCCESS) {
            state = EngineState.NoArabic
            return
        }
        engine.setOnUtteranceProgressListener(listener)
        state = if (selectArabic(engine)) EngineState.ArabicReady else EngineState.NoArabic
    }

    private fun selectArabic(engine: TextToSpeech): Boolean {
        val candidates = listOf("ar", "ar-SA", "ar-EG").map { Locale.forLanguageTag(it) }
        for (locale in candidates) {
            if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) continue
            if (engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) return true
        }
        return false // no Arabic voice, or its data isn't installed
    }

    override fun speak(text: String, utteranceId: String): SpeakResult {
        val engine = tts
        if (released || engine == null) return SpeakResult.Unavailable
        return when (state) {
            EngineState.Initializing -> SpeakResult.NotReady
            EngineState.NoArabic -> SpeakResult.Unavailable
            EngineState.ArabicReady -> try {
                // QUEUE_FLUSH drops whatever is playing, so two readings can never overlap
                val r = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (r == TextToSpeech.SUCCESS) SpeakResult.Started else SpeakResult.Unavailable
            } catch (e: Exception) {
                Log.w(QUIZ_LOG_TAG, "speak failed: ${e.message}")
                SpeakResult.Unavailable
            }
        }
    }

    override fun stop() {
        try { tts?.stop() } catch (e: Exception) { Log.w(QUIZ_LOG_TAG, "stop failed: ${e.message}") }
    }

    override fun release() {
        released = true
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(QUIZ_LOG_TAG, "release failed: ${e.message}")
        }
        tts = null
    }
}

/** What the quiz UI sees: a status to render, and speak/stop. */
@Stable
class QuizSpeech internal constructor(
    private val controller: QuizSpeechController,
    private val state: State<SpeechStatus>
) {
    val status: SpeechStatus get() = state.value
    fun speak(text: String) = controller.speak(text)
    fun stop() = controller.stop()
    internal fun release() = controller.release()
}

/** One speech engine per quiz overlay; released automatically when the overlay leaves composition. */
@Composable
fun rememberQuizSpeech(): QuizSpeech {
    val appContext = LocalContext.current.applicationContext
    val state = remember { mutableStateOf(SpeechStatus.Idle) }
    val speech = remember {
        lateinit var controller: QuizSpeechController
        val engine = AndroidSpeechEngine(appContext, object : AndroidSpeechEngine.Callbacks {
            override fun onFinished(utteranceId: String) = controller.onUtteranceFinished(utteranceId)
            override fun onFailed(utteranceId: String) = controller.onUtteranceFailed(utteranceId)
        })
        controller = QuizSpeechController(engine) { state.value = it }
        QuizSpeech(controller, state)
    }
    DisposableEffect(speech) { onDispose { speech.release() } }
    return speech
}
