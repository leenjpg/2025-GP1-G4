package com.example.aimoduel


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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp


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
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityServiceEnabled()) {
            isServiceEnabledState.value = true


            requestBatteryExemption()
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


//  (Jetpack Compose)


private val AlfontDark = FontFamily(
    Font(R.font.alfont_com_dark, FontWeight.Normal)
)

@Composable
fun ChildSetupScreen(isServiceEnabled: Boolean, onEnableClick: () -> Unit) {
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFC0D4DF)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "حامي",
            fontSize = 64.sp,
            color = Color(0xFF52879C),
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
            text = "🛡️",
            fontSize = 100.sp,
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(60.dp))

        Button(
            onClick = onEnableClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RectangleShape,
            enabled = !isServiceEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceEnabled) Color(0xFF4CAF50) else Color(0xFF52879C),
                disabledContainerColor = Color(0xFF4CAF50),
                disabledContentColor = Color.White,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(
                text = if (isServiceEnabled) "تم تفعيل الحماية" else "تفعيل الحماية",
                fontSize = 20.sp,
                fontFamily = AlfontDark
            )
        }
    }
}