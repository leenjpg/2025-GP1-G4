package com.example.aimoduel


import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ArrowBack

// تعريف الخط
private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))

class LoginActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager()

        setContent {
            LaunchedEffect(Unit) {
                if (authManager.isParentAuthenticated()) {
                    navigateToDashboard()
                }
            }
            MainLoginScreen(
                onLoginSuccess = { navigateToDashboard() },
                authManager = authManager
            )
        }
    }

    private fun navigateToDashboard() {
        // card for parent
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().putString("USER_ROLE", "PARENT").apply()

        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun MainLoginScreen(onLoginSuccess: () -> Unit, authManager: AuthManager) {
    var currentScreen by remember { mutableStateOf(LoginScreenType.LOGIN) }
    val hamiTeal = Color(0xFF52879C)
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "حامي",
                color = hamiTeal,
                fontSize = 56.sp,
                fontFamily = AlfontDark,
                modifier = Modifier.padding(top = 32.dp)
            )

            Text(
                text = "لحماية أطفالك عبر الإنترنت",
                color = Color(0xFF788B94),
                fontSize = 16.sp,
                fontFamily = AlfontDark,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            when (currentScreen) {
                LoginScreenType.LOGIN -> {
                    LoginScreenContent(
                        onNavigateToSignUp = { currentScreen = LoginScreenType.SIGNUP },
                        onNavigateToForgotPassword = { currentScreen = LoginScreenType.FORGOT_PASSWORD },
                        onLoginSuccess = onLoginSuccess,
                        authManager = authManager
                    )
                }
                LoginScreenType.SIGNUP -> {
                    SignUpScreenContent(
                        onNavigateBack = { currentScreen = LoginScreenType.LOGIN },
                        onSignUpSuccess = onLoginSuccess,
                        authManager = authManager
                    )
                }
                LoginScreenType.FORGOT_PASSWORD -> {
                    ForgotPasswordScreenContent(
                        onNavigateBack = { currentScreen = LoginScreenType.LOGIN },
                        authManager = authManager
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreenContent(
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit,
    authManager: AuthManager
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hamiTeal = Color(0xFF52879C)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "تسجيل الدخول",
            fontSize = 24.sp,
            fontFamily = AlfontDark,
            color = hamiTeal,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("كلمة المرور", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onNavigateToForgotPassword, enabled = !isLoading) {
                Text("نسيت كلمة المرور؟", color = hamiTeal, fontSize = 14.sp, fontFamily = AlfontDark)
            }
        }

        errorMessage?.let {
            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp, fontFamily = AlfontDark)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    authManager.loginParent(email, password) { success, error ->
                        if (success) onLoginSuccess() else errorMessage = error ?: "فشل تسجيل الدخول"
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = hamiTeal),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("دخول", color = Color.White, fontSize = 20.sp, fontFamily = AlfontDark)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("ليس لديك حساب؟ ", fontFamily = AlfontDark, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = onNavigateToSignUp, enabled = !isLoading) {
                Text("إنشاء حساب جديد", color = hamiTeal, fontFamily = AlfontDark)
            }
        }
    }
}

@Composable
fun SignUpScreenContent(
    onNavigateBack: () -> Unit,
    onSignUpSuccess: () -> Unit,
    authManager: AuthManager
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val hamiTeal = Color(0xFF52879C)

    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onNavigateBack, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = hamiTeal)
        }

        Text(
            text = "إنشاء حساب جديد",
            fontSize = 24.sp,
            fontFamily = AlfontDark,
            color = hamiTeal,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("الاسم الكامل", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("كلمة المرور", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("تأكيد كلمة المرور", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = password != confirmPassword && confirmPassword.isNotEmpty()
        )

        Text(
            text = "كلمة المرور يجب أن تكون 6 أحرف على الأقل",
            fontSize = 12.sp,
            fontFamily = AlfontDark,
            color = if (password.length >= 6) Color(0xFF4CAF50) else Color.Gray,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )

        successMessage?.let {
            Text(text = it, color = Color(0xFF4CAF50), modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp, fontFamily = AlfontDark)
        }

        errorMessage?.let {
            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp, fontFamily = AlfontDark)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    fullName.isBlank() -> errorMessage = "الاسم الكامل مطلوب"
                    email.isBlank() -> errorMessage = "البريد الإلكتروني مطلوب"
                    password.isBlank() -> errorMessage = "كلمة المرور مطلوبة"
                    password.length < 6 -> errorMessage = "كلمة المرور يجب أن تكون 6 أحرف على الأقل"
                    password != confirmPassword -> errorMessage = "كلمة المرور غير متطابقة"
                    else -> {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            authManager.signupParent(email, password) { success, error ->
                                if (success) {
                                    successMessage = "تم إنشاء الحساب بنجاح! جاري تسجيل الدخول..."
                                    authManager.loginParent(email, password) { loginSuccess, loginError ->
                                        if (loginSuccess) onSignUpSuccess() else errorMessage = loginError ?: "فشل تسجيل الدخول التلقائي"
                                    }
                                } else {
                                    errorMessage = error ?: "فشل إنشاء الحساب"
                                }
                                isLoading = false
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RectangleShape, // زر حاد
            colors = ButtonDefaults.buttonColors(containerColor = hamiTeal),
            enabled = !isLoading && password.length >= 6 && password == confirmPassword
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("إنشاء حساب", color = Color.White, fontSize = 20.sp, fontFamily = AlfontDark)
        }
    }
}

@Composable
fun ForgotPasswordScreenContent(
    onNavigateBack: () -> Unit,
    authManager: AuthManager
) {
    var email by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hamiTeal = Color(0xFF52879C)

    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onNavigateBack, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = hamiTeal)
        }

        Text(
            text = "استعادة كلمة المرور",
            fontSize = 24.sp,
            fontFamily = AlfontDark,
            color = hamiTeal,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "سنرسل لك رابطاً لإعادة تعيين كلمة المرور على بريدك الإلكتروني",
            fontSize = 14.sp,
            fontFamily = AlfontDark,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني", fontFamily = AlfontDark) },
            leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )

        successMessage?.let {
            Text(text = it, color = Color(0xFF4CAF50), modifier = Modifier.padding(top = 16.dp), fontSize = 14.sp, fontFamily = AlfontDark)
        }

        errorMessage?.let {
            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 16.dp), fontSize = 14.sp, fontFamily = AlfontDark)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    successMessage = null
                    authManager.resetPassword(email) { success, message ->
                        if (success) {
                            successMessage = message ?: "تم إرسال الرابط"
                            email = ""
                        } else {
                            errorMessage = message ?: "فشل إرسال الرابط"
                        }
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RectangleShape, // زر حاد
            colors = ButtonDefaults.buttonColors(containerColor = hamiTeal),
            enabled = !isLoading && email.isNotBlank()
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("إرسال رابط إعادة التعيين", color = Color.White, fontSize = 20.sp, fontFamily = AlfontDark)
        }
    }
}

enum class LoginScreenType { LOGIN, SIGNUP, FORGOT_PASSWORD }