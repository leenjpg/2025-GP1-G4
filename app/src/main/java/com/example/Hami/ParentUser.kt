package com.example.Hami

data class ParentUser(
    val uid: String,
    val email: String,
    val displayName: String = "",
    val isEmailVerified: Boolean = false,
    val createdAt: com.google.firebase.Timestamp? = null
)

data class ChildProfile(
    val childId: String,
    val parentId: String,
    val childName: String,
    val age: Int,
    val deviceId: String = "",
    val createdAt: com.google.firebase.Timestamp? = null,
    val accessibilityServiceEnabled: Boolean = false,
    val safetySettings: Map<String, Any> = mapOf(
        "aiEnabled" to true,
        "educationTipsEnabled" to true
    )
)