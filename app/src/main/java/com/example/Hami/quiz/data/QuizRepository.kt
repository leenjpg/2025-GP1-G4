package com.example.Hami.quiz.data

import android.content.Context
import android.util.Log
import com.example.Hami.quiz.DEFAULT_QUESTION_TIME
import com.example.Hami.quiz.QUIZ_ASSET_FILE
import com.example.Hami.quiz.QUIZ_EASY_ASSET_FILE
import com.example.Hami.quiz.QUIZ_LOG_TAG
import com.example.Hami.quiz.model.Quiz
import com.example.Hami.quiz.model.QuizDifficulty
import com.example.Hami.quiz.model.QuizQuestion
import org.json.JSONObject
import java.util.Locale

/*
   Loads quiz content from the local asset file (works fully offline).
   Fails safe: any read/parse problem is logged and results in "no quiz", never a crash.
 */
class QuizRepository(private val context: Context) {

    @Volatile
    private var cache: List<Quiz>? = null

    /** Returns one quiz for the category (random if several exist), or null if none is available. */
    fun getQuizForCategory(category: String, difficulty: QuizDifficulty = QuizDifficulty.MEDIUM): Quiz? {
        return try {
            val key = normalizeCategory(category)
            loadAll().filter { normalizeCategory(it.category) == key && it.difficulty == difficulty }.randomOrNull()
        } catch (e: Exception) {
            Log.e(QUIZ_LOG_TAG, "getQuizForCategory failed: ${e.message}")
            null
        }
    }

    /** All valid quizzes (used by the weekly milestone selector). */
    fun getAllQuizzes(): List<Quiz> = loadAll()

    @Synchronized
    private fun loadAll(): List<Quiz> {
        cache?.let { return it }
        val parsed = try {
            // Keep the existing medium content intact; the easy bank is an independent offline asset.
            listOf(QUIZ_ASSET_FILE, QUIZ_EASY_ASSET_FILE).flatMap { asset ->
                val text = context.assets.open(asset).bufferedReader().use { it.readText() }
                parse(text)
            }
        } catch (e: Exception) {
            Log.e(QUIZ_LOG_TAG, "Could not load $QUIZ_ASSET_FILE: ${e.message}")
            emptyList()
        }
        if (parsed.isNotEmpty()) cache = parsed // don't cache a failure, so a later call can retry
        return parsed
    }

    private fun parse(jsonText: String): List<Quiz> {
        val quizzes = mutableListOf<Quiz>()
        val array = JSONObject(jsonText).getJSONArray("quizzes")
        for (i in 0 until array.length()) {
            try {
                parseQuiz(array.getJSONObject(i))?.let { quizzes.add(it) }
            } catch (e: Exception) {
                Log.e(QUIZ_LOG_TAG, "Skipping invalid quiz at index $i: ${e.message}")
            }
        }
        return quizzes
    }

    private fun parseQuiz(obj: JSONObject): Quiz? {
        val id = obj.getString("id")
        val difficulty = when (obj.optString("difficulty", "medium").lowercase(Locale.ROOT)) {
            "easy" -> QuizDifficulty.EASY
            "medium" -> QuizDifficulty.MEDIUM
            else -> throw IllegalArgumentException("Unknown quiz difficulty in '$id'")
        }
        // Younger readers get more time. The existing timer never auto-advances or fails them.
        val defaultTime = if (difficulty == QuizDifficulty.EASY) 20 else DEFAULT_QUESTION_TIME
        val questionsJson = obj.getJSONArray("questions")
        val questions = mutableListOf<QuizQuestion>()

        for (i in 0 until questionsJson.length()) {
            val q = questionsJson.getJSONObject(i)
            val optionsJson = q.getJSONArray("options")
            val options = (0 until optionsJson.length()).map { optionsJson.getString(it) }
            val correct = q.getInt("correctAnswerIndex")

            // A broken question invalidates the whole quiz (better no quiz than a wrong one)
            if (options.size < 2 || correct !in options.indices || q.getString("question").isBlank()) {
                Log.e(QUIZ_LOG_TAG, "Quiz '$id' question #$i is invalid - quiz skipped")
                return null
            }
            questions.add(
                QuizQuestion(
                    id = q.getString("id"),
                    question = q.getString("question"),
                    options = options,
                    correctAnswerIndex = correct,
                    explanation = q.getString("explanation"),
                    timerSeconds = q.optInt("timerSeconds", defaultTime).coerceAtLeast(1)
                )
            )
        }
        if (questions.isEmpty()) return null

        return Quiz(
            id = id,
            category = obj.getString("category"),
            title = obj.optString("title", ""),
            takeaway = obj.optString("takeaway", ""),
            questions = questions,
            difficulty = difficulty
        )
    }

    companion object {
        /** "Religious Discrimination", "religious_discrimination " -> "religious discrimination" */
        fun normalizeCategory(category: String): String =
            category.trim().lowercase(Locale.ROOT).replace('_', ' ')
    }
}
