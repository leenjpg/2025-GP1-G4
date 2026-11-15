package com.example.Hami.quiz.manager

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log

import com.example.Hami.quiz.QUIZ_LOG_TAG
import com.example.Hami.quiz.QUIZ_TRIGGER_THRESHOLD
import com.example.Hami.quiz.data.QuizRepository
import com.example.Hami.quiz.model.Quiz
import com.example.Hami.quiz.model.QuizDifficulty
import com.example.Hami.quiz.ui.QuizOverlayController

/*
   Owns the quiz decisions:
   - Weekly threshold check
   - Age-based quiz selection
   - Question history
   - Overlay launching
   - Pending queue draining
   - Quiz completion tracking

   Must be called on the main thread.
 */
object QuizManager {

    @Volatile
    private var repository: QuizRepository? = null

    /**
     * True while the quiz overlay is on screen.
     */
    val isQuizShowing: Boolean
        get() = QuizOverlayController.isShowing()

    /**
     * Returns true only when the fifth valid alert
     * of the week has been reached.
     *
     * QuizAlertCounter guarantees that the weekly
     * trigger is created only once.
     */
    fun shouldTriggerQuiz(alertCount: Int): Boolean =
        alertCount == QUIZ_TRIGGER_THRESHOLD

    private fun repo(context: Context): QuizRepository =
        repository ?: QuizRepository(
            context.applicationContext
        ).also {
            repository = it
        }

    /**
     * Returns all educational quizzes.
     */
    fun getAllQuizzes(context: Context): List<Quiz> =
        repo(context).getAllQuizzes()

    /**
     * Returns a quiz matching the detected category
     * and the child's difficulty level.
     */
    fun getQuizForCategory(
        context: Context,
        category: String,
        difficulty: QuizDifficulty = QuizDifficulty.MEDIUM
    ): Quiz? =
        repo(context).getQuizForCategory(
            category,
            difficulty
        )

    /**
     * Displays the quiz overlay.
     *
     * Existing functionality is preserved:
     * - No duplicate overlays
     * - Question history
     * - Age-adaptive quizzes
     * - Original completion callback
     * - Existing TTS and overlay behavior
     */
    fun launchQuiz(
        service: AccessibilityService,
        quiz: Quiz,
        childId: String? = null,
        onCompleted: () -> Unit = {}
    ): Boolean {

        if (isQuizShowing) {
            return false
        }

        val appContext = service.applicationContext

        return try {

            val history =
                if (childId != null) {
                    QuizHistory(appContext)
                } else {
                    null
                }

            QuizOverlayController.show(
                service,
                quiz,
                onQuestionShown = { questionId ->

                    if (childId != null) {
                        history?.markPresented(
                            childId,
                            questionId
                        )
                    }
                },
                onFinished = {

                    // Called only after the child
                    // completes the quiz.
                    onCompleted()
                }
            )

        } catch (exception: Exception) {

            Log.e(
                QUIZ_LOG_TAG,
                "Could not launch quiz overlay",
                exception
            )

            false
        }
    }

    /**
     * Displays the oldest pending weekly quiz.
     *
     * Quiz selection still depends on:
     * - Last detected alert category
     * - Child's registered age
     * - Previously displayed question IDs
     *
     * New functionality:
     * - Record that the quiz was assigned
     * - Record when the quiz was completed
     *
     * All existing trigger and selection logic
     * remains unchanged.
     */
    fun showNextPending(
        service: AccessibilityService,
        childId: String
    ) {

        val context = service.applicationContext

        val counter = QuizAlertCounter(context)

        val history = QuizHistory(context)

        val profile = context.getSharedPreferences(
            "HamiPrefs",
            Context.MODE_PRIVATE
        )

        val age =
            if (profile.contains("CHILD_AGE")) {
                profile.getInt(
                    "CHILD_AGE",
                    -1
                )
            } else {
                null
            }

        val difficulty = QuizDifficulty.forAge(age)

        // Preserve existing age validation.
        if (difficulty == null) {

            Log.i(
                QUIZ_LOG_TAG,
                "Quiz not shown: registered age $age " +
                        "is below the supported range"
            )

            // Remove owed quizzes without
            // changing the alert counter.
            while (true) {

                val trigger =
                    counter.peekPending(childId) ?: break

                counter.consumePending(
                    childId,
                    trigger
                )
            }

            return
        }

        while (!isQuizShowing) {

            val trigger =
                counter.peekPending(childId) ?: return

            val all = getAllQuizzes(context)

            when (
                val result = QuizSelector.select(
                    all,
                    trigger.category,
                    history.getPresentedIds(childId),
                    difficulty
                )
            ) {

                is QuizSelector.Result.Selected -> {

                    val selectedQuiz = result.quiz

                    val launched = launchQuiz(
                        service = service,
                        quiz = selectedQuiz,
                        childId = childId
                    ) {

                        /*
                         * NEW:
                         * This callback runs after the child
                         * presses Finish on the quiz overlay.
                         */
                        QuizProgressManager.markCompleted(
                            childId = childId,
                            week = trigger.week,
                            quiz = selectedQuiz
                        )

                        /*
                         * ORIGINAL:
                         * Continue processing pending quizzes.
                         */
                        showNextPending(
                            service,
                            childId
                        )
                    }

                    if (launched) {

                        /*
                         * NEW:
                         * Store the pending status only
                         * after the overlay appears.
                         */
                        QuizProgressManager.markAssigned(
                            childId = childId,
                            week = trigger.week,
                            quiz = selectedQuiz
                        )

                        /*
                         * ORIGINAL:
                         * Consume the weekly trigger.
                         */
                        counter.consumePending(
                            childId,
                            trigger
                        )

                        Log.d(
                            QUIZ_LOG_TAG,
                            "Weekly quiz ${trigger.milestone} " +
                                    "(${trigger.category}, $difficulty) " +
                                    "-> quiz ${selectedQuiz.id}"
                        )

                    } else {

                        Log.w(
                            QUIZ_LOG_TAG,
                            "Launch failed; weekly quiz remains queued"
                        )
                    }

                    return
                }

                QuizSelector.Result.Exhausted -> {

                    Log.w(
                        QUIZ_LOG_TAG,
                        "Question pool EXHAUSTED for " +
                                "'${trigger.category}' ($difficulty). " +
                                "Weekly quiz skipped."
                    )

                    counter.consumePending(
                        childId,
                        trigger
                    )
                }

                QuizSelector.Result.UnknownCategory -> {

                    Log.e(
                        QUIZ_LOG_TAG,
                        "Unknown quiz category " +
                                "'${trigger.category}' - " +
                                "no quiz shown."
                    )

                    counter.consumePending(
                        childId,
                        trigger
                    )
                }
            }
        }
    }
}