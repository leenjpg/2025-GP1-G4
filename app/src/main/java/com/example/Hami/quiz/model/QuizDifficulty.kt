package com.example.Hami.quiz.model

/** Age-adapted question banks. The child's age is read from the existing local profile. */
enum class QuizDifficulty {
    EASY, MEDIUM;

    companion object {
        fun forAge(age: Int?): QuizDifficulty? {
            val value = age ?: return MEDIUM
            return when (value) {
                in 6..8 -> EASY
                in 9..18 -> MEDIUM
                else -> null
            }
        }
    }
}
