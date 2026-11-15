package com.example.Hami.quiz.model

/** Age-adapted question banks. The child's age is read from the existing local profile. */
enum class QuizDifficulty {
    EASY, MEDIUM;

    companion object {
        /**
         * 6–8: concrete, short questions. 9–12: scenario-based questions.
         * Older existing Hami profiles keep the medium bank for compatibility.
         * Under-6 profiles do not receive questions designed for school-age children.
         * Missing age uses the previous (medium) content until the profile is updated.
         */
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
