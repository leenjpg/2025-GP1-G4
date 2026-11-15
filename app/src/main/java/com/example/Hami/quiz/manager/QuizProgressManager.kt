package com.example.Hami.quiz.manager

import android.util.Log
import com.example.Hami.quiz.QUIZ_LOG_TAG
import com.example.Hami.quiz.model.Quiz
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Saves quiz assignment and completion information.
 *
 * Data is stored in the existing child document:
 *
 * child/{childId}
 *   quizProgress:
 *     {weekStart}:
 *       status
 *       category
 *       quizId
 *       questionCount
 *       assignedAt
 *       completedAt
 *
 * No question texts, selected answers, or harmful messages
 * are stored by this class.
 */
object QuizProgressManager {

    /**
     * Called after the quiz overlay has appeared successfully.
     */
    fun markAssigned(
        childId: String,
        week: String,
        quiz: Quiz
    ) {
        saveProgress(
            childId = childId,
            week = week,
            fields = mapOf(
                "status" to "pending",
                "category" to quiz.category,
                "quizId" to quiz.id,
                "questionCount" to quiz.questions.size,
                "assignedAt" to FieldValue.serverTimestamp()
            )
        )
    }

    /**
     * Called after the child finishes the quiz.
     */
    fun markCompleted(
        childId: String,
        week: String,
        quiz: Quiz
    ) {
        saveProgress(
            childId = childId,
            week = week,
            fields = mapOf(
                "status" to "completed",
                "category" to quiz.category,
                "quizId" to quiz.id,
                "questionCount" to quiz.questions.size,
                "completedAt" to FieldValue.serverTimestamp()
            )
        )
    }

    private fun saveProgress(
        childId: String,
        week: String,
        fields: Map<String, Any>
    ) {
        if (childId.isBlank() || week.isBlank()) {
            Log.w(
                QUIZ_LOG_TAG,
                "Quiz progress skipped: invalid childId or week"
            )
            return
        }

        try {
            val data = mapOf(
                "quizProgress" to mapOf(
                    week to fields
                )
            )

            FirebaseFirestore.getInstance()
                .collection("child")
                .document(childId)
                .set(
                    data,
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    Log.d(
                        QUIZ_LOG_TAG,
                        "Quiz progress saved: child=$childId, week=$week"
                    )
                }
                .addOnFailureListener { exception ->
                    Log.e(
                        QUIZ_LOG_TAG,
                        "Failed to save quiz progress",
                        exception
                    )
                }

        } catch (exception: Exception) {
            // Never interrupt the quiz because of a tracking error.
            Log.e(
                QUIZ_LOG_TAG,
                "Quiz progress tracking error",
                exception
            )
        }
    }
}