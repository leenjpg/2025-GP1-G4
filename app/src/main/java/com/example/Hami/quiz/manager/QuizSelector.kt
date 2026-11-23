package com.example.Hami.quiz.manager

import com.example.Hami.quiz.model.Quiz
import com.example.Hami.quiz.model.QuizDifficulty
import java.util.Locale

/*
 * Pure selection logic (no Android).
 *
 * 1. Prefer questions the child has NEVER seen within the alert category and age level.
 * 2. When ALL matching questions have been shown, reuse the LEAST RECENTLY shown quiz.
 * 3. Never select a different alert category or difficulty as a fallback.
 *
 * [presented] must preserve last-shown order (oldest first). QuizHistory returns a
 * LinkedHashSet and moves each re-shown question ID to the end of that set.
 */
object QuizSelector {

    private val NOT_ALERTS = setOf("neutral", "normal")

    fun normalize(category: String): String =
        category.trim().lowercase(Locale.ROOT).replace('_', ' ')

    /** True if this is a non-neutral category available in the question bank. */
    fun isKnownCategory(all: List<Quiz>, category: String): Boolean {
        val key = normalize(category)
        return key.isNotEmpty() && key !in NOT_ALERTS && all.any { normalize(it.category) == key }
    }

    sealed class Result {
        data class Selected(val quiz: Quiz) : Result()
        object Exhausted : Result() // No questions exist for the requested category + difficulty.
        object UnknownCategory : Result()
    }

    /**
     * Selects previously unseen questions first (preserving the existing behavior).
     *
     * If no unseen questions remain, selects the quiz whose newest question was shown
     * furthest in the past (least-recently-used quiz). Its questions are also sorted
     * from oldest to newest display time.
     *
     * The parent/child flow, weekly trigger, and question count per quiz are unchanged.
     */
    fun select(
        all: List<Quiz>, category: String, presented: Set<String>,
        difficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
        random: kotlin.random.Random = kotlin.random.Random
    ): Result {
        if (!isKnownCategory(all, category)) return Result.UnknownCategory
        val key = normalize(category)

        val matching = all.filter {
            normalize(it.category) == key && it.difficulty == difficulty && it.questions.isNotEmpty()
        }
        if (matching.isEmpty()) return Result.Exhausted

        // First use every new question before repeating any previously displayed question.
        val freshCandidates = matching
            .map { quiz -> quiz to quiz.questions.filter { it.id !in presented } }
            .filter { it.second.isNotEmpty() }

        if (freshCandidates.isNotEmpty()) {
            val mostFresh = freshCandidates.maxOf { it.second.size }
            val (quiz, fresh) = freshCandidates.filter { it.second.size == mostFresh }.random(random)
            return Result.Selected(quiz.copy(questions = fresh))
        }

        // All questions have been seen. Prefer whichever quiz was used longest ago.
        // History is a LinkedHashSet ordered by most recent PRESENTATION, not first-ever use.
        val lastShownRank = presented.withIndex().associate { it.value to it.index }
        val fallbackRank = Int.MAX_VALUE

        // A quiz is as recently used as its most recently displayed question.
        // The smallest rank means the whole quiz was last seen furthest in the past.
        val ranked = matching.map { quiz ->
            quiz to (quiz.questions.maxOfOrNull { lastShownRank[it.id] ?: fallbackRank }
                ?: fallbackRank)
        }
        val oldestRank = ranked.minOf { it.second }
        val chosen = ranked.filter { it.second == oldestRank }.random(random).first

        val orderedQuestions = chosen.questions.sortedBy { lastShownRank[it.id] ?: fallbackRank }
        return Result.Selected(chosen.copy(questions = orderedQuestions))
    }
}
