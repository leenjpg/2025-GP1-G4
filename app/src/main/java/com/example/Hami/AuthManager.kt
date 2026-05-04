package com.example.Hami

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase

class AuthManager {
    private val auth: FirebaseAuth = Firebase.auth

    // Login Function
    fun loginParent(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        // Validate input first
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "البريد الإلكتروني وكلمة المرور مطلوبان")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    val errorMsg = when {
                        task.exception?.message?.contains("no user record") == true ->
                            "لا يوجد حساب بهذا البريد الإلكتروني"
                        task.exception?.message?.contains("password is invalid") == true ->
                            "كلمة المرور غير صحيحة"
                        task.exception?.message?.contains("network error") == true ->
                            "خطأ في الاتصال بالشبكة"
                        task.exception?.message?.contains("invalid email") == true ->
                            "البريد الإلكتروني غير صالح"
                        else -> task.exception?.message ?: "حدث خطأ غير متوقع"
                    }
                    onResult(false, errorMsg)
                }
            }
            .addOnFailureListener { exception ->
                onResult(false, exception.message ?: "فشل الاتصال بالخادم")
            }
    }
    // Signup function
    fun signupParent(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "البريد الإلكتروني وكلمة المرور مطلوبان")
            return
        }

        if (password.length < 6) {
            onResult(false, "كلمة المرور يجب أن تكون 6 أحرف على الأقل")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    val errorMsg = when {
                        task.exception?.message?.contains("email address is already in use") == true ->
                            "البريد الإلكتروني مستخدم بالفعل"
                        task.exception?.message?.contains("invalid email") == true ->
                            "البريد الإلكتروني غير صالح"
                        task.exception?.message?.contains("weak password") == true ->
                            "كلمة المرور ضعيفة"
                        else -> task.exception?.message ?: "حدث خطأ في التسجيل"
                    }
                    onResult(false, errorMsg)
                }
            }
    }
    // Reset Password function
    fun resetPassword(email: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank()) {
            onResult(false, "البريد الإلكتروني مطلوب")
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, "تم إرسال رابط إعادة التعيين إلى بريدك الإلكتروني")
                } else {
                    val errorMsg = when {
                        task.exception?.message?.contains("no user record") == true ->
                            "لا يوجد حساب بهذا البريد الإلكتروني"
                        task.exception?.message?.contains("invalid email") == true ->
                            "البريد الإلكتروني غير صالح"
                        else -> task.exception?.message ?: "حدث خطأ في إرسال رابط إعادة التعيين"
                    }
                    onResult(false, errorMsg)
                }
            }
            .addOnFailureListener { exception ->
                onResult(false, exception.message ?: "فشل الاتصال بالخادم")
            }
    }

    // Logout function
    fun logoutParent() {
        auth.signOut()
    }

    // Check if user is authenticated
    fun isParentAuthenticated(): Boolean = auth.currentUser != null

    // Get current user
    fun getCurrentUser(): ParentUser? {
        val user = auth.currentUser
        return user?.let {
            ParentUser(
                uid = it.uid,
                email = it.email ?: ""
            )
        }
    }
}