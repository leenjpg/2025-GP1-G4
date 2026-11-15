package com.example.Hami.quiz.manager

import com.example.Hami.quiz.model.Quiz
import com.example.Hami.quiz.model.QuizDifficulty
import java.util.Locale

/*
   Pure selection logic (no Android): picks a quiz of the milestone's category that still has
   questions the child has never been shown. Never falls back to another category.
 */
object QuizSelector {

    private val NOT_ALERTS = setOf("neutral", "normal")

    fun normalize(category: String): String =
        category.trim().lowercase(Locale.ROOT).replace('_', ' ')

    /** True if [category] can legitimately own a quiz (non-blank, not Neutral/Normal, exists in content). */
    fun isKnownCategory(all: List<Quiz>, category: String): Boolean {
        val key = normalize(category)
        return key.isNotEmpty() && key !in NOT_ALERTS && all.any { normalize(it.category) == key }
    }

    sealed class Result {
        data class Selected(val quiz: Quiz) : Result()
        object Exhausted : Result()
        object UnknownCategory : Result()
    }

    /**
     * Returned quiz contains ONLY unpresented questions (a copy of the stored quiz), preferring the
     * quiz with the most unused questions so quizzes stay whole whenever possible.
     */
    fun select(
        all: List<Quiz>, category: String, presented: Set<String>,
        difficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
        random: kotlin.random.Random = kotlin.random.Random
    ): Result {
        if (!isKnownCategory(all, category)) return Result.UnknownCategory
        val key = normalize(category)
        val candidates = all.filter { normalize(it.category) == key && it.difficulty == difficulty }
            .map { quiz -> quiz to quiz.questions.filter { it.id !in presented } }
            .filter { it.second.isNotEmpty() }
        if (candidates.isEmpty()) return Result.Exhausted
        val best = candidates.maxOf { it.second.size }
        val (quiz, fresh) = candidates.filter { it.second.size == best }.random(random)
        return Result.Selected(quiz.copy(questions = fresh))
    }
}
