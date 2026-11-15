package com.example.Hami.quiz.model

import com.example.Hami.quiz.DEFAULT_QUESTION_TIME

data class QuizQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String,
    val timerSeconds: Int = DEFAULT_QUESTION_TIME
)
