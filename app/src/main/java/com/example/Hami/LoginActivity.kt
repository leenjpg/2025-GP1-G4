package com.example.Hami

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))

class LoginActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Test Firestore connection
        val db = FirebaseFirestore.getInstance()
        db.collection("test").document("test").set(hashMapOf("test" to "value"))
            .addOnSuccessListener { android.util.Log.d("Firestore", "✅ Working") }
            .addOnFailureListener { android.util.Log.e("Firestore", "❌ Failed: ${it.message}") }

        authManager = AuthManager()

        setContent {
            val sharedPref = androidx.compose.ui.platform.LocalContext.current
                .getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
            val isParentProfileComplete = sharedPref.getBoolean("PARENT_PROFILE_COMPLETE", false)

            // إجبار الواجهة تكون من اليمين لليسار (RTL)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                if (authManager.isParentAuthenticated() && isParentProfileComplete) {
                    navigateToDashboard()
                } else {
                    MainLoginScreen(
                        onLoginSuccess = { parentUser ->
                            // For existing users, just navigate
                            FirebaseFirestore.getInstance().collection("parent").document(parentUser.uid).get()
                                .addOnSuccessListener { doc ->
                                    if (doc.exists()) {
                                        markProfileComplete(parentUser.uid, parentUser.email, doc.getString("displayName") ?: "")
                                        navigateToDashboard()
                                    } else saveParentToFirestore(parentUser, parentUser.displayName)
                                }
                                .addOnFailureListener { android.widget.Toast.makeText(this@LoginActivity, "تعذر جلب بيانات الحساب", android.widget.Toast.LENGTH_LONG).show() }
                        },
                        onSignUpSuccess = { parentUser, fullName ->
                            // Save to Firestore FIRST, then navigate
                            saveParentToFirestore(parentUser, fullName)
                        },
                        authManager = authManager
                    )
                }
            }
        }
    }

    private fun saveParentToFirestore(parentUser: ParentUser, fullName: String) {
        val db = FirebaseFirestore.getInstance()
        val parentData = hashMapOf(
            "uid" to parentUser.uid,
            "email" to parentUser.email,
            "displayName" to fullName,
            "createdAt" to Timestamp.now()
        )

        db.collection("parent").document(parentUser.uid)
            .set(parentData)
            .addOnSuccessListener {
                android.util.Log.d("LoginActivity", "✅ Parent saved to Firestore: ${parentUser.uid}")
                // Mark profile complete and navigate
                markProfileComplete(parentUser.uid, parentUser.email, fullName)
                navigateToDashboard()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("LoginActivity", "❌ Failed to save to Firestore: ${e.message}")
                // Still navigate even if Firestore fails? No, show error
                android.widget.Toast.makeText(
                    this@LoginActivity,
                    "فشل حفظ البيانات: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun markProfileComplete(uid: String, email: String, name: String) {
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putBoolean("PARENT_PROFILE_COMPLETE", true)
            if (name.isNotEmpty()) putString("PARENT_NAME", name)
            putString("PARENT_ID", uid)
            putString("PARENT_EMAIL", email)
            apply()
        }
    }

    private fun navigateToDashboard() {
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().putString("USER_ROLE", "PARENT").apply()

        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun MainLoginScreen(
    onLoginSuccess: (ParentUser) -> Unit,
    onSignUpSuccess: (ParentUser, String) -> Unit,
    authManager: AuthManager
) {
    var currentScreen by remember { mutableStateOf(if (authManager.getCurrentUser()?.isEmailVerified == false) LoginScreenType.VERIFY else LoginScreenType.LOGIN) }
    val context = LocalContext.current
    var pendingName by remember { mutableStateOf("") }
    var sendInitially by remember { mutableStateOf(false) }
    HamiAuthLayout {
        HamiAuthHeader()
        when (currentScreen) {
            LoginScreenType.LOGIN -> {
                LoginScreenContent(
                    onNavigateToSignUp = { currentScreen = LoginScreenType.SIGNUP },
                    onNavigateToForgotPassword = { currentScreen = LoginScreenType.FORGOT_PASSWORD },
                    onLoginSuccess = { user ->
                        if (user.isEmailVerified) onLoginSuccess(user)
                        else { sendInitially = true; currentScreen = LoginScreenType.VERIFY }
                    },
                    authManager = authManager
                )
            }
            LoginScreenType.SIGNUP -> {
                SignUpScreenContent(
                    onNavigateBack = { currentScreen = LoginScreenType.LOGIN },
                    onSignUpSuccess = { _, name ->
                        pendingName = name
                        sendInitially = true
                        currentScreen = LoginScreenType.VERIFY
                    },
                    authManager = authManager
                )
            }
            LoginScreenType.VERIFY -> EmailVerificationScreen(
                pendingName = pendingName, sendInitially = sendInitially, authManager = authManager,
                onBack = { authManager.logoutParent(); currentScreen = LoginScreenType.LOGIN },
                onVerified = { user ->
                    val name = user.displayName.ifBlank { pendingName }
                    FirebaseFirestore.getInstance().collection("parent").document(user.uid).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) onLoginSuccess(user) else onSignUpSuccess(user, name)
                        }
                        .addOnFailureListener { android.widget.Toast.makeText(context, "تعذر جلب بيانات الحساب، حاول مرة أخرى", android.widget.Toast.LENGTH_LONG).show() }
                }
            )
            LoginScreenType.FORGOT_PASSWORD -> {
                ForgotPasswordScreenContent(
                    onNavigateBack = { currentScreen = LoginScreenType.LOGIN },
                    authManager = authManager
                )
            }
        }
    }
}

@Composable
fun LoginScreenContent(
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: (ParentUser) -> Unit,
    authManager: AuthManager
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hamiTeal = Color(0xFF52879C)

    Column(modifier = Modifier.fillMaxWidth()) {
        HamiAuthTitle("تسجيل الدخول", bottomPadding = 24.dp)

        HamiAuthTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            // 🔥 النص داخل الحقل إنجليزي لليسار
            textStyle = LocalTextStyle.current.copy(
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Left
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        HamiAuthTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("كلمة المرور", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            // 🔥 النص داخل الحقل إنجليزي لليسار
            textStyle = LocalTextStyle.current.copy(
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Left
            )
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

        HamiAuthButton(
            label = "تسجيل الدخول",
            isLoading = isLoading,
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    authManager.loginParent(email, password) { success, error ->
                        if (success) {
                            val currentUser = authManager.getCurrentUser()
                            if (currentUser != null) {
                                onLoginSuccess(currentUser)
                            } else {
                                errorMessage = "حدث خطأ في جلب معلومات المستخدم"
                                isLoading = false
                            }
                        } else {
                            // 🔥 استخدام الدالة العربية الموحدة
                            errorMessage = getArabicErrorMessage(error)
                            isLoading = false
                        }
                    }
                }
            },
            enabled = !isLoading
        )

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
    onSignUpSuccess: (ParentUser, String) -> Unit,
    authManager: AuthManager
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordFocused by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hamiTeal = Color(0xFF52879C)

    Column(modifier = Modifier.fillMaxWidth()) {
        HamiAuthTitle("إنشاء حساب جديد", bottomPadding = 24.dp, onBack = onNavigateBack)

        HamiAuthTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("الاسم الكامل", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            // 🔥 الاسم الكامل يقرأ حسب لغة الإدخال
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
        )

        Spacer(modifier = Modifier.height(16.dp))

        HamiAuthTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            // 🔥 إيميل (يسار)
            textStyle = LocalTextStyle.current.copy(
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Left
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        HamiAuthTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("كلمة المرور", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().onFocusChanged { passwordFocused = it.isFocused },
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            // 🔥 باسورد (يسار)
            textStyle = LocalTextStyle.current.copy(
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Left
            )
        )

        if (passwordFocused) {
            Text(
                text = "كلمة المرور يجب أن تكون 6 أحرف على الأقل",
                fontSize = 12.sp,
                fontFamily = AlfontDark,
                color = if (password.length >= 6) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HamiAuthTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("تأكيد كلمة المرور", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = password != confirmPassword && confirmPassword.isNotEmpty(),
            // 🔥 تأكيد الباسورد (يسار)
            textStyle = LocalTextStyle.current.copy(
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Left
            )
        )


        errorMessage?.let {
            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp, fontFamily = AlfontDark)
        }

        Spacer(modifier = Modifier.height(24.dp))

        HamiAuthButton(
            label = "إنشاء حساب",
            isLoading = isLoading,
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
                                    val currentUser = authManager.getCurrentUser()
                                    if (currentUser != null) {
                                        // Pass to parent to save to Firestore
                                        onSignUpSuccess(currentUser, fullName)
                                    } else {
                                        errorMessage = "حدث خطأ في إنشاء الحساب"
                                        isLoading = false
                                    }
                                } else {
                                    // 🔥 رسائل خطأ عربية في التسجيل
                                    errorMessage = getArabicErrorMessage(error)
                                    isLoading = false
                                }
                            }
                        }
                    }
                }
            },
            enabled = !isLoading && fullName.isNotBlank() && email.isNotBlank() && password.length >= 6 && password == confirmPassword
        )
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
        HamiAuthTitle("استعادة كلمة المرور", bottomPadding = 8.dp, onBack = onNavigateBack)

        Text(
            text = "سنرسل لك رابطاً لإعادة تعيين كلمة المرور على بريدك الإلكتروني",
            fontSize = 14.sp,
            fontFamily = AlfontDark,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            textAlign = TextAlign.Start
        )

        HamiAuthTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني", fontFamily = AlfontDark) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            // 🔥 إيميل الاستعادة (يسار)
            textStyle = LocalTextStyle.current.copy(
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Left
            )
        )

        successMessage?.let {
            Text(text = it, color = Color(0xFF4CAF50), modifier = Modifier.padding(top = 16.dp), fontSize = 14.sp, fontFamily = AlfontDark)
        }

        errorMessage?.let {
            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 16.dp), fontSize = 14.sp, fontFamily = AlfontDark)
        }

        Spacer(modifier = Modifier.height(24.dp))

        HamiAuthButton(
            label = "إرسال رابط إعادة التعيين",
            isLoading = isLoading,
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    successMessage = null
                    authManager.resetPassword(email) { success, message ->
                        if (success) {
                            successMessage = "تم إرسال الرابط إلى بريدك الإلكتروني بنجاح"
                            email = ""
                        } else {
                            // 🔥 استخدام المترجم العربي حتى في رسائل الاستعادة
                            errorMessage = getArabicErrorMessage(message)
                        }
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && email.isNotBlank()
        )
    }
}

enum class LoginScreenType { LOGIN, SIGNUP, FORGOT_PASSWORD, VERIFY }

private val authTeal = Color(0xFF52879C)

@Composable
internal fun HamiAuthLayout(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(
        colors = listOf(Color.White, Color(0xFFC0D4DF))
    )).padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
internal fun HamiAuthHeader() {
    Text("حامي", color = authTeal, fontSize = 56.sp, fontFamily = AlfontDark,
        modifier = Modifier.padding(top = 32.dp))
    Spacer(modifier = Modifier.height(10.dp))
    Text("لحماية أطفالك عبر الإنترنت", color = Color(0xFF788B94), fontSize = 16.sp,
        fontFamily = AlfontDark, modifier = Modifier.padding(bottom = 32.dp))
}

@Composable
internal fun HamiAuthTitle(
    text: String,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 24.sp, fontFamily = AlfontDark, color = authTeal,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        if (onBack != null) {
            HamiAuthBackButton(onClick = onBack)
        }
    }
}

@Composable
internal fun HamiAuthBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = authTeal)
    }
}

@Composable
internal fun HamiAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    isError: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = label, leadingIcon = leadingIcon,
        modifier = modifier, enabled = enabled, keyboardOptions = keyboardOptions,
        singleLine = singleLine, isError = isError, visualTransformation = visualTransformation,
        textStyle = textStyle, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = authTeal, unfocusedBorderColor = Color(0xFFB9CBD3),
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedLabelColor = authTeal, focusedLeadingIconColor = authTeal,
            unfocusedLeadingIconColor = authTeal
        )
    )
}

@Composable
internal fun HamiAuthButton(label: String, isLoading: Boolean, onClick: () -> Unit, enabled: Boolean) {
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = authTeal)) {
        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        else Text(label, color = Color.White, fontSize = 20.sp, fontFamily = AlfontDark)
    }
}

@Composable
private fun EmailVerificationScreen(pendingName: String, sendInitially: Boolean, authManager: AuthManager, onBack: () -> Unit, onVerified: (ParentUser) -> Unit) {
    val scope = rememberCoroutineScope()
    var remaining by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun send() {
        busy = true
        message = null
        try {
            authManager.sendEmailVerification(pendingName)
            sent = true
            remaining = 60
        } catch (e: Exception) { message = "تعذر إرسال رابط التحقق، حاول مرة أخرى" }
        finally { busy = false }
    }
    LaunchedEffect(Unit) { if (sendInitially) send() }
    LaunchedEffect(remaining) { if (remaining > 0) { delay(1000); remaining-- } }
    HamiAuthTitle("تأكيد البريد الإلكتروني", onBack = onBack)
    Icon(Icons.Default.Email, contentDescription = null, tint = authTeal, modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(16.dp))
    Text(if (sent) "تم إرسال رابط التحقق إلى بريدك الإلكتروني." else "يرجى تأكيد بريدك الإلكتروني قبل المتابعة.", fontFamily = AlfontDark, textAlign = TextAlign.Center)
    Text(authManager.getCurrentUser()?.email.orEmpty(), color = authTeal, fontFamily = AlfontDark)
    Spacer(Modifier.height(12.dp))
    Text("يرجى فتح الرابط لتأكيد بريدك الإلكتروني قبل المتابعة.", color = Color.Gray, fontFamily = AlfontDark, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    HamiAuthButton("تم التحقق", busy, onClick = { scope.launch {
        busy = true; message = null
        try {
            val verifiedUser = authManager.verifyEmail()
            if (verifiedUser != null) {
                onVerified(verifiedUser)
            } else message = "لم يتم تأكيد البريد الإلكتروني بعد"
        } catch (e: Exception) { message = "تعذر التحقق، حاول مرة أخرى" }
        finally { busy = false }
    } }, enabled = !busy)
    TextButton(onClick = { scope.launch { send() } }, enabled = !busy && remaining == 0) {
        Text(if (remaining > 0) "إعادة إرسال رابط التحقق ($remaining)" else "إعادة إرسال رابط التحقق", fontFamily = AlfontDark, color = authTeal)
    }
    message?.let { Text(it, color = Color.Red, fontFamily = AlfontDark) }
}
