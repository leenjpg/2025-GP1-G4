package com.example.Hami

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))

fun getArabicErrorMessage(error: String?): String {
    if (error == null) return "حدث خطأ غير معروف"
    val lowerError = error.lowercase()
    return when {

        lowerError.contains("password") || lowerError.contains("credential") -> "البريد الإلكتروني أو كلمة المرور غير صحيحة"
        lowerError.contains("email") || lowerError.contains("user not found") -> "البريد الإلكتروني غير مسجل أو غير صحيح"
        lowerError.contains("network") -> "تأكد من اتصالك بالإنترنت"
        lowerError.contains("too many requests") -> "محاولات كثيرة خاطئة، يرجى المحاولة لاحقاً"
        lowerError.contains("format") -> "صيغة البريد الإلكتروني غير صحيحة"
        else -> "فشل تسجيل الدخول: يرجى المحاولة مرة أخرى"
    }
}

class ChildLoginActivity : ComponentActivity() {
    private val authManager = AuthManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already logged in as child
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        val isChildLoggedIn = sharedPref.getBoolean("CHILD_LOGGED_IN", false)
        val childId = sharedPref.getString("CHILD_ID", "")

        if (isChildLoggedIn && !childId.isNullOrEmpty()) {
            navigateToChildStatus()
            return
        }

        setContent {
            // (RTL)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ChildLoginScreen(
                    onLoginSuccess = { parentUser, selectedChild ->
                        saveChildLogin(parentUser, selectedChild)
                        navigateToChildStatus()
                    },
                    onCreateNewChild = { parentUser, childName, childAge ->
                        createNewChild(parentUser, childName, childAge)
                        navigateToChildStatus()
                    },
                    authManager = authManager
                )
            }
        }
    }

    private fun saveChildLogin(parentUser: ParentUser, selectedChild: ChildProfile) {
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        sharedPref.edit().apply {
            putBoolean("CHILD_LOGGED_IN", true)
            putString("USER_ROLE", "CHILD")
            putString("CHILD_ID", selectedChild.childId)
            putString("CHILD_NAME", selectedChild.childName)
            putInt("CHILD_AGE", selectedChild.age)
            putString("DEVICE_ID", deviceId)
            putString("PARENT_ID", parentUser.uid)
            putString("PARENT_EMAIL", parentUser.email)
            apply()
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("child").document(selectedChild.childId)
            .update(
                "deviceId", deviceId,
                "lastActiveAt", Timestamp.now(),
                "accessibilityServiceEnabled", false
            )
            .addOnSuccessListener {
                android.util.Log.d("ChildLogin", "✅ Child device linked: ${selectedChild.childName}")
            }
    }

    private fun createNewChild(parentUser: ParentUser, childName: String, childAge: Int) {
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        val childId = "child_${System.currentTimeMillis()}"
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        sharedPref.edit().apply {
            putBoolean("CHILD_LOGGED_IN", true)
            putString("USER_ROLE", "CHILD")
            putString("CHILD_ID", childId)
            putString("CHILD_NAME", childName)
            putInt("CHILD_AGE", childAge)
            putString("DEVICE_ID", deviceId)
            putString("PARENT_ID", parentUser.uid)
            putString("PARENT_EMAIL", parentUser.email)
            apply()
        }

        val db = FirebaseFirestore.getInstance()
        val childData = hashMapOf(
            "childId" to childId,
            "parentId" to parentUser.uid,
            "childName" to childName,
            "age" to childAge,
            "deviceId" to deviceId,
            "createdAt" to Timestamp.now(),
            "accessibilityServiceEnabled" to false
        )

        db.collection("child").document(childId)
            .set(childData)
            .addOnSuccessListener {
                android.util.Log.d("ChildLogin", "✅ New child created: $childName")
            }
    }

    private fun navigateToChildStatus() {
        val intent = Intent(this, ChildStatusActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun ChildLoginScreen(
    onLoginSuccess: (ParentUser, ChildProfile) -> Unit,
    onCreateNewChild: (ParentUser, String, Int) -> Unit,
    authManager: AuthManager
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var childrenList by remember { mutableStateOf<List<ChildProfile>>(emptyList()) }
    var selectedChild by remember { mutableStateOf<ChildProfile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var showCreateChildForm by remember { mutableStateOf(false) }

    // New child form fields
    var newChildName by remember { mutableStateOf("") }
    var newChildAge by remember { mutableStateOf("") }

    val hamiTeal = Color(0xFF52879C)

    HamiAuthLayout {
        HamiAuthHeader()
        if (!isAuthenticated) {
            // Step 1: Parent Login
            HamiAuthTitle("إعداد جهاز الطفل", bottomPadding = 8.dp)
            Text("الرجاء تسجيل الدخول بحساب ولي الأمر", fontSize = 14.sp, color = Color.Gray, fontFamily = AlfontDark, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), textAlign = TextAlign.Start)

            HamiAuthTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني", fontFamily = AlfontDark) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),

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
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),

                textStyle = LocalTextStyle.current.copy(
                    textDirection = TextDirection.Ltr,
                    textAlign = TextAlign.Left
                )
            )

            errorMessage?.let {
                Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontFamily = AlfontDark)
            }

            Spacer(modifier = Modifier.height(32.dp))

            HamiAuthButton(label = "تسجيل الدخول", isLoading = isLoading,
                onClick = {
                    isLoading = true
                    errorMessage = null
                    authManager.loginParent(email, password) { success, error ->
                        if (success) {
                            val currentUser = authManager.getCurrentUser()
                            if (currentUser != null && !currentUser.isEmailVerified) {
                                authManager.logoutParent()
                                errorMessage = "يرجى تأكيد البريد الإلكتروني من حساب ولي الأمر أولاً"
                                isLoading = false
                            } else if (currentUser != null) {
                                val db = FirebaseFirestore.getInstance()
                                db.collection("child")
                                    .whereEqualTo("parentId", currentUser.uid)
                                    .get()
                                    .addOnSuccessListener { result ->
                                        val children = result.documents.mapNotNull { doc ->
                                            ChildProfile(
                                                childId = doc.getString("childId") ?: "",
                                                parentId = doc.getString("parentId") ?: "",
                                                childName = doc.getString("childName") ?: "",
                                                age = doc.getLong("age")?.toInt() ?: 0,
                                                deviceId = doc.getString("deviceId") ?: "",
                                                accessibilityServiceEnabled = doc.getBoolean("accessibilityServiceEnabled") ?: false
                                            )
                                        }
                                        childrenList = children
                                        isAuthenticated = true
                                        isLoading = false
                                    }
                                    .addOnFailureListener {
                                        errorMessage = "فشل في جلب بيانات الأطفال"
                                        isLoading = false
                                    }
                            } else {
                                errorMessage = "حدث خطأ في جلب معلومات المستخدم"
                                isLoading = false
                            }
                        } else {
                            errorMessage = getArabicErrorMessage(error)
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            )
        } else if (showCreateChildForm) {
            // Step 2b: Create New Child Form
            HamiAuthTitle("إضافة طفل جديد", bottomPadding = 8.dp)
            Text("أدخل معلومات الطفل الجديد", fontSize = 14.sp, color = Color.Gray, fontFamily = AlfontDark, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), textAlign = TextAlign.Start)

            HamiAuthTextField(
                value = newChildName,
                onValueChange = { newChildName = it },
                label = { Text("اسم الطفل", fontFamily = AlfontDark) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,

                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HamiAuthTextField(
                value = newChildAge,
                onValueChange = { newChildAge = it },
                label = { Text("عمر الطفل", fontFamily = AlfontDark) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(
                    textDirection = TextDirection.Content
                )
            )

            errorMessage?.let {
                Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontFamily = AlfontDark)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        showCreateChildForm = false
                        newChildName = ""
                        newChildAge = ""
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("إلغاء", color = Color.White, fontSize = 16.sp, fontFamily = AlfontDark)
                }

                Button(
                    onClick = {
                        if (newChildName.isBlank()) {
                            errorMessage = "اسم الطفل مطلوب"
                            return@Button
                        }
                        val age = newChildAge.toIntOrNull()
                        if (age == null || age < 1 || age > 18) {
                            errorMessage = "عمر الطفل يجب أن يكون بين 1 و 18"
                            return@Button
                        }
                        val parentUser = authManager.getCurrentUser()
                        if (parentUser != null) {
                            onCreateNewChild(parentUser, newChildName, age)
                        } else {
                            errorMessage = "حدث خطأ"
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = hamiTeal)
                ) {
                    Text("إضافة", color = Color.White, fontSize = 16.sp, fontFamily = AlfontDark)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { showCreateChildForm = false }) {
                Text("العودة لاختيار الطفل", color = hamiTeal, fontSize = 14.sp, fontFamily = AlfontDark)
            }

        } else {
            // Step 2a: Select Child or Create New
            HamiAuthTitle("اختر الطفل", bottomPadding = 8.dp)
            Text("اختر اسم الطفل لهذا الجهاز", fontSize = 14.sp, color = Color.Gray, fontFamily = AlfontDark, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), textAlign = TextAlign.Start)

            // Show existing children (if any)
            if (childrenList.isNotEmpty()) {
                childrenList.forEach { child ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                selectedChild = child
                            },
                        shape = RoundedCornerShape(16.dp),
                        border = if (selectedChild?.childId == child.childId) androidx.compose.foundation.BorderStroke(1.dp, hamiTeal) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB9CBD3)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedChild?.childId == child.childId)
                                Color(0xFFF5FAFC)
                            else
                                Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = child.childName,
                                    fontSize = 18.sp,
                                    fontFamily = AlfontDark,
                                    color = hamiTeal
                                )
                                Text(
                                    text = "العمر: ${child.age} ${if (child.age >= 11) "سنة" else "سنوات"}",
                                    fontSize = 12.sp,
                                    fontFamily = AlfontDark,
                                    color = Color.Gray
                                )
                            }
                            if (selectedChild?.childId == child.childId) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = hamiTeal, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else{
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Text(
                        "لم تتم إضافة أي أطفال بعد، قم بإضافة الأطفال من تطبيق ولي الأمر أولًا",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontFamily = AlfontDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }


            errorMessage?.let {
                Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontFamily = AlfontDark)
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (childrenList.isNotEmpty()) {
                HamiAuthButton(label = "تأكيد وربط الجهاز", isLoading = isLoading,
                    onClick = {
                        if (selectedChild == null && childrenList.isNotEmpty()) {
                            errorMessage = "الرجاء اختيار طفل"
                            return@HamiAuthButton
                        }
                        if (selectedChild != null) {
                            isLoading = true
                            val parentUser = authManager.getCurrentUser()
                            if (parentUser != null && selectedChild != null) {
                                onLoginSuccess(parentUser, selectedChild!!)
                            } else {
                                errorMessage = "حدث خطأ"
                                isLoading = false
                            }
                        } else if (childrenList.isEmpty()) {
                            errorMessage = "الرجاء إضافة طفل أولاً"
                        }
                    },
                    enabled = !isLoading && selectedChild != null
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    isAuthenticated = false
                    email = ""
                    password = ""
                    childrenList = emptyList()
                    selectedChild = null
                }
            ) {
                Text("تسجيل دخول بحساب آخر", color = hamiTeal, fontSize = 14.sp, fontFamily = AlfontDark)
            }
        }
    }
}