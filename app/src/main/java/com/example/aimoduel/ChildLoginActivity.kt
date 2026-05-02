package com.example.aimoduel

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))

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

    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
    )
    val hamiTeal = Color(0xFF52879C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isAuthenticated) {
            // Step 1: Parent Login
            Text("إعداد جهاز الطفل", color = hamiTeal, fontSize = 32.sp, fontFamily = AlfontDark)
            Text("الرجاء تسجيل الدخول بحساب الوالدين", fontSize = 14.sp, color = Color.Gray, fontFamily = AlfontDark)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني للوالد", fontFamily = AlfontDark) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("كلمة المرور", fontFamily = AlfontDark) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            errorMessage?.let {
                Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontFamily = AlfontDark)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    authManager.loginParent(email, password) { success, error ->
                        if (success) {
                            val currentUser = authManager.getCurrentUser()
                            if (currentUser != null) {
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
                            errorMessage = error ?: "فشل تسجيل الدخول"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = hamiTeal),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("تسجيل الدخول", color = Color.White, fontSize = 20.sp, fontFamily = AlfontDark)
                }
            }
        } else if (showCreateChildForm) {
            // Step 2b: Create New Child Form
            Text("إضافة طفل جديد", color = hamiTeal, fontSize = 32.sp, fontFamily = AlfontDark)
            Text("أدخل معلومات الطفل الجديد", fontSize = 14.sp, color = Color.Gray, fontFamily = AlfontDark)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = newChildName,
                onValueChange = { newChildName = it },
                label = { Text("اسم الطفل", fontFamily = AlfontDark) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = newChildAge,
                onValueChange = { newChildAge = it },
                label = { Text("عمر الطفل", fontFamily = AlfontDark) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                    shape = RectangleShape,
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
                    shape = RectangleShape,
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
            Text("اختر الطفل", color = hamiTeal, fontSize = 32.sp, fontFamily = AlfontDark)
            Text("اختر اسم الطفل لهذا الجهاز", fontSize = 14.sp, color = Color.Gray, fontFamily = AlfontDark)
            Spacer(modifier = Modifier.height(32.dp))

            // Show existing children (if any)
            if (childrenList.isNotEmpty()) {
                childrenList.forEach { child ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                selectedChild = child
                            },
                        shape = RectangleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedChild?.childId == child.childId)
                                hamiTeal.copy(alpha = 0.2f)
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
                                    text = "العمر: ${child.age} سنة",
                                    fontSize = 12.sp,
                                    fontFamily = AlfontDark,
                                    color = Color.Gray
                                )
                            }
                            if (selectedChild?.childId == child.childId) {
                                Text("✅", fontSize = 24.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Button to create new child (always visible)
            Button(
                onClick = {
                    selectedChild = null
                    showCreateChildForm = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة طفل جديد", color = Color.White, fontSize = 16.sp, fontFamily = AlfontDark)
            }

            errorMessage?.let {
                Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontFamily = AlfontDark)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (selectedChild == null && childrenList.isNotEmpty()) {
                        errorMessage = "الرجاء اختيار طفل"
                        return@Button
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
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = hamiTeal),
                enabled = !isLoading && (selectedChild != null || childrenList.isEmpty())
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("تأكيد وربط الجهاز", color = Color.White, fontSize = 18.sp, fontFamily = AlfontDark)
                }
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