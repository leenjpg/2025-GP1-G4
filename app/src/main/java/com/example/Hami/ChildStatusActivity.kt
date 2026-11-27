package com.example.Hami

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

class ChildStatusActivity : ComponentActivity() {
    private var isServiceEnabledState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isServiceEnabledState.value = isAccessibilityServiceEnabled()

        setContent {
            ChildSetupScreen(
                isServiceEnabled = isServiceEnabledState.value,
                onEnableClick = {
                    openAccessibilitySettings()
                },
                onLogout = {
                    logout()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val isEnabled = isAccessibilityServiceEnabled()
        if (isEnabled && !isServiceEnabledState.value) {
            isServiceEnabledState.value = true
            updateFirestoreServiceStatus(true)
            requestBatteryExemption()
        }
    }

    private fun logout() {
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        val childId = sharedPref.getString("CHILD_ID", "")

        // 1. Update service status in Firestore before clearing prefs
        if (!childId.isNullOrEmpty()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("child").document(childId)
                .update("accessibilityServiceEnabled", false)
        }

        // 2. Clear saved preferences (this sets CHILD_LOGGED_IN to false)
        sharedPref.edit().clear().apply()

        // 3. Sign out from Firebase Auth
        val authManager = AuthManager()
        authManager.logoutParent()

        // 4. Navigate back to Main / Login Activity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        Toast.makeText(this, "تم تسجيل الخروج", Toast.LENGTH_SHORT).show()
    }

    private fun updateFirestoreServiceStatus(enabled: Boolean) {
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        val childId = sharedPref.getString("CHILD_ID", "") ?: return
        val parentId = sharedPref.getString("PARENT_ID", "") ?: return

        val db = FirebaseFirestore.getInstance()
        db.collection("child").document(childId)
            .update("accessibilityServiceEnabled", enabled)
            .addOnSuccessListener {
                android.util.Log.d("ChildStatus", "✅ Service status updated to: $enabled")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChildStatus", "❌ Failed to update: ${e.message}")
            }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Toast.makeText(this, "يرجى البحث عن 'Hami' وتفعيل الصلاحية.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestBatteryExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, HamiAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) return true
        }
        return false
    }
}

@Composable
fun ChildSetupScreen(isServiceEnabled: Boolean, onEnableClick: () -> Unit, onLogout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false)}
    var logoutPassword by remember { mutableStateOf("")}
    var logoutError by remember { mutableStateOf<String?>(null)}
    var isCheckingPassword by remember { mutableStateOf(false)}
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
    )
    val hamiTeal = Color(0xFF52879C)
// ask for parent password before logging out on child device
    if (showLogoutDialog){
        AlertDialog(
            onDismissRequest = { if (!isCheckingPassword) showLogoutDialog = false},
            title = { Text("تأكيد تسجيل الخروج", fontFamily = AlfontDark, color = hamiTeal, fontSize = 20.sp)},
            text = {
                Column{
                    Text(
                        "أدخل كلمة مرور ولي الأمر لتسجيل الخروج من هذا الجهاز",
                        fontFamily = AlfontDark,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = logoutPassword,
                        onValueChange = {logoutPassword = it; logoutError = null},
                        label = {Text("كلمة المرور", fontFamily = AlfontDark)},
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                        ),
                        singleLine = true,
                        enabled = !isCheckingPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                    logoutError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = Color.Red, fontSize = 12.sp, fontFamily = AlfontDark)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isCheckingPassword,
                    onClick = {
                        if(logoutPassword.isBlank()){
                            logoutError = "يرجى إدخال كلمة المرور"
                            return@TextButton
                        }
                        val parentEmail = context
                            .getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
                            .getString("PARENT_EMAIL", "") ?: ""
                        isCheckingPassword = true
                        AuthManager().loginParent(parentEmail, logoutPassword){success, error ->
                            isCheckingPassword = false
                            if(success){ //for the firebase enabled update after logout for correct deletion of children
                                val childId = context
                                    .getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
                                    .getString("CHILD_ID", "") ?: ""
                                if (childId.isNotEmpty()){
                                    isCheckingPassword = true
                                    FirebaseFirestore.getInstance().collection("child").document(childId)
                                        .update("accessibilityServiceEnabled", false)
                                        .addOnCompleteListener {
                                            isCheckingPassword = false
                                            showLogoutDialog = false
                                            onLogout()
                                        }
                                } else{
                                    showLogoutDialog = false
                                    onLogout()
                                }
                            } else {
                                logoutError = "كلمة المرور غير صحيحة"
                            }
                        }
                    }
                ) {
                    if (isCheckingPassword){
                        CircularProgressIndicator(color = hamiTeal, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)

                    } else{
                        Text("تأكيد", fontFamily = AlfontDark, color = hamiTeal)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false}, enabled = !isCheckingPassword){
                    Text("إلغاء", fontFamily = AlfontDark)
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }
    val statusColor = if (isServiceEnabled) Color(0xFF388E3C) else hamiTeal
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().background(backgroundGradient)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Button(
                    onClick = {
                        logoutPassword = ""
                        logoutError = null
                        showLogoutDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(40.dp)
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text("تسجيل الخروج", fontFamily = AlfontDark, color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("حامي", fontSize = 64.sp, color = hamiTeal, fontFamily = AlfontDark)
            Spacer(Modifier.height(10.dp))
            Text("حماية ذكية، خصوصية تامة", fontSize = 18.sp, color = Color(0xFF788B94), fontFamily = AlfontDark)
            Spacer(Modifier.height(40.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(96.dp).background(statusColor.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(54.dp)) {
                            val w = size.width
                            val h = size.height
                            val shield = Path().apply {
                                moveTo(w * .5f, h * .06f)
                                lineTo(w * .88f, h * .22f)
                                lineTo(w * .84f, h * .58f)
                                quadraticBezierTo(w * .78f, h * .82f, w * .5f, h * .95f)
                                quadraticBezierTo(w * .22f, h * .82f, w * .16f, h * .58f)
                                lineTo(w * .12f, h * .22f)
                                close()
                            }
                            drawPath(shield, statusColor, style = Stroke(width = 3.dp.toPx()))
                            if (isServiceEnabled) {
                                drawLine(statusColor, Offset(w*.30f,h*.49f), Offset(w*.44f,h*.63f), 3.dp.toPx(), StrokeCap.Round)
                                drawLine(statusColor, Offset(w*.44f,h*.63f), Offset(w*.70f,h*.36f), 3.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(if (isServiceEnabled) "الحماية مفعلة" else "الحماية غير مفعلة", fontSize = 24.sp, fontFamily = AlfontDark, color = statusColor)
                    Spacer(Modifier.height(8.dp))
                    Text(if (isServiceEnabled) "جهاز الطفل محمي الآن" else "اضغط على الزر أدناه لتفعيل الحماية", fontSize = 14.sp, fontFamily = AlfontDark, color = Color.Gray, textAlign = TextAlign.Center)
                    if (!isServiceEnabled) {
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onEnableClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = hamiTeal), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)) {
                            Text("تفعيل الحماية", fontSize = 18.sp, fontFamily = AlfontDark, color = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))