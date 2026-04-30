package com.example.aimoduel

data class ParentUser(
    val uid: String,
    val email: String,
    val isEmailVerified: Boolean = false
)