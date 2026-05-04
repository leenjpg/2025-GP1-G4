package com.example.Hami

data class AlertItem(
    val text: String,
    val riskLabel: String,
    val timestamp: Long,
    val confidence: Float,
    val childId: String,
    val parentId: String,
    val read: Boolean = false,
    val context: String = "keyboard_input",
    val documentId: String = ""
)