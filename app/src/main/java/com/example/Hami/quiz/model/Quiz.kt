package com.example.Hami.quiz.model

/**
 * @param category  Same label string produced by AiEngine ("Offensive", "Sexism",
 *                  "Religious Discrimination", "Racism"). Matching is case-insensitive.
 * @param takeaway  One short, kind reminder shown after a wrong answer and on the completion screen.
 */
data class Quiz(
    val id: String,
    val category: String,
    val title: String,
    val takeaway: String,
    val questions: List<QuizQuestion>,
    val difficulty: QuizDifficulty = QuizDifficulty.MEDIUM
)
