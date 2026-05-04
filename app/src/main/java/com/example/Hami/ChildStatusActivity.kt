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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
        // Clear saved data
        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        // Sign out from Firebase Auth if parent is logged in
        val authManager = AuthManager()
        authManager.logoutParent()

        // Go back to main activity
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
        // Logout button at top right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RectangleShape
            ) {
                Text("خروج", fontSize = 14.sp, fontFamily = AlfontDark, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "حامي",
            fontSize = 64.sp,
            color = hamiTeal,
            fontFamily = AlfontDark
        )

        Text(
            text = "حماية ذكية، خصوصية تامة",
            fontSize = 18.sp,
            color = Color(0xFF788B94),
            fontFamily = AlfontDark,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = if (isServiceEnabled) "✅" else "🛡️",
            fontSize = 100.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isServiceEnabled) {
            Text(
                text = "الحماية مفعلة",
                fontSize = 24.sp,
                fontFamily = AlfontDark,
                color = Color(0xFF4CAF50)
            )
            Text(
                text = "جهاز الطفل محمي الآن",
                fontSize = 14.sp,
                fontFamily = AlfontDark,
                color = Color.Gray
            )
        } else {
            Text(
                text = "الحماية غير مفعلة",
                fontSize = 24.sp,
                fontFamily = AlfontDark,
                color = hamiTeal
            )
            Text(
                text = "اضغط على الزر أدناه لتفعيل الحماية",
                fontSize = 14.sp,
                fontFamily = AlfontDark,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onEnableClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = hamiTeal),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text("تفعيل الحماية", fontSize = 20.sp, fontFamily = AlfontDark, color = Color.White)
            }
        }
    }
}

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))