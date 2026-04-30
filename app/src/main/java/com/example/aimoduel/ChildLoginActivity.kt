package com.example.aimoduel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// تعريف الخط الخاص بالشاشة
private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))

class ChildLoginActivity : ComponentActivity() {
    private val authManager = AuthManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChildLoginScreen(
                onLoginSuccess = {
                    // save card for child
                    val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
                    sharedPref.edit().putString("USER_ROLE", "CHILD").apply()

                    val intent = Intent(this@ChildLoginActivity, ChildStatusActivity::class.java)
                    startActivity(intent)
                    finish()
                },
                authManager = authManager
            )
        }
    }
}

@Composable
fun ChildLoginScreen(onLoginSuccess: () -> Unit, authManager: AuthManager) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
    )
    val hamiTeal = Color(0xFF52879C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient) // تطبيق التدرج
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("إعداد جهاز الطفل", color = hamiTeal, fontSize = 32.sp, fontFamily = AlfontDark)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني", fontFamily = AlfontDark) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("كلمة المرور", fontFamily = AlfontDark) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        errorMessage?.let {
            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontFamily = AlfontDark)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                authManager.loginParent(email, password) { success, error ->
                    if (success) onLoginSuccess() else errorMessage = "فشل تسجيل الدخول"
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RectangleShape, // زر حاد الأطراف
            colors = ButtonDefaults.buttonColors(containerColor = hamiTeal)
        ) {
            Text("ربط الجهاز", color = Color.White, fontSize = 20.sp, fontFamily = AlfontDark)
        }
    }
}