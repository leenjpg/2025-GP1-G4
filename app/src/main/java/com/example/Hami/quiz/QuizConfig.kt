package com.example.Hami.quiz

/*
   Central configuration for the educational quiz feature.
   Change a value here and it applies everywhere - no other file hard-codes these numbers.
 */

/** A quiz is triggered at every multiple of this many valid alerts within the same calendar week (Mon-Sun). */
const val QUIZ_TRIGGER_THRESHOLD = 5

/** Default seconds per question (a question may override it with "timerSeconds" in quizzes.json). */
const val DEFAULT_QUESTION_TIME = 10

/** Local quiz content file inside app/src/main/assets. */
const val QUIZ_ASSET_FILE = "quizzes.json"

/** New, simpler question bank for ages 6–8; the original asset remains unchanged. */
const val QUIZ_EASY_ASSET_FILE = "quizzes_easy.json"

/** Log tag for every quiz-related log line. */
const val QUIZ_LOG_TAG = "HamiQuiz"
