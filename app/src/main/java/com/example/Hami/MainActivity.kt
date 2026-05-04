package com.example.Hami

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import com.google.firebase.auth.FirebaseAuth

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))

class MainActivity : ComponentActivity() {
    private val authManager = AuthManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
        val userRole = sharedPref.getString("USER_ROLE", null)
        val isAuthenticated = authManager.isParentAuthenticated()

        // If user is authenticated but role is missing, fix it
        if (isAuthenticated && userRole == null) {
            // Save the role based on what we know
            sharedPref.edit().putString("USER_ROLE", "PARENT").apply()
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        // Normal flow
        if (isAuthenticated) {
            if (userRole == "CHILD") {
                startActivity(Intent(this, ChildStatusActivity::class.java))
                finish()
                return
            } else if (userRole == "PARENT") {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
                return
            }
        }

        setContent {
            val backgroundGradient = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundGradient)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "مرحباً بك في حامي",
                    fontSize = 32.sp,
                    color = Color(0xFF52879C),
                    fontFamily = AlfontDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "حدد نوع المستخدم للمتابعة",
                    fontSize = 16.sp,
                    color = Color(0xFF788B94),
                    fontFamily = AlfontDark
                )
                Spacer(modifier = Modifier.height(60.dp))

                Button(
                    onClick = { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52879C))
                ) {
                    Text("دخول كولي أمر (الأب / الأم)", fontSize = 18.sp, fontFamily = AlfontDark)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { startActivity(Intent(this@MainActivity, ChildLoginActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF788B94))
                ) {
                    Text("إعداد هذا الجهاز للطفل", fontSize = 18.sp, fontFamily = AlfontDark)
                }
            }
        }
    }
}