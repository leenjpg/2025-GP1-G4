package com.example.Hami

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Warning
import com.google.firebase.firestore.Query

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))


// Shared typography for dashboard pages.
@Composable
private fun HamiPageTitle(text: String) {
    Text(
        text = text,
        fontSize = 28.sp,
        fontFamily = AlfontDark,
        fontWeight = FontWeight.Normal,
        color = Color(0xFF52879C)
    )
}

@Composable
private fun HamiSectionTitle(text: String, fontSize: androidx.compose.ui.unit.TextUnit = 18.sp) {
    Text(
        text = text,
        fontSize = fontSize,
        fontFamily = AlfontDark,
        fontWeight = FontWeight.Normal,
        color = Color(0xFF52879C)
    )
}

@Composable
private fun HamiNote(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontFamily = AlfontDark,
        fontWeight = FontWeight.Normal,
        color = Color.Gray
    )
}

val threatTypes = listOf("offensive", "sexism", "religious discrimination", "racism")
val threatDisplayNames = mapOf(
    "offensive" to "هجومي",
    "sexism" to "عنصرية جنسية",
    "religious discrimination" to "تعصب ديني",
    "racism" to "عنصرية عرقية"
)
val threatColors = mapOf(
    "offensive" to Color(0xFF1976D2),
    "sexism" to Color(0xFF2196F3),
    "religious discrimination" to Color(0xFF64B5F6),
    "racism" to Color(0xFF90CAF9)
)

val daysOfWeek = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
val dayOfWeekMapping = mapOf(
    Calendar.SATURDAY to 0,
    Calendar.SUNDAY to 1,
    Calendar.MONDAY to 2,
    Calendar.TUESDAY to 3,
    Calendar.WEDNESDAY to 4,
    Calendar.THURSDAY to 5,
    Calendar.FRIDAY to 6
)

sealed class NavItem(val title: String, val icon: ImageVector) {
    object Safety : NavItem("توعية", Icons.Filled.Info)
    object Report : NavItem("تقارير", Icons.Filled.DateRange)
    object Alerts : NavItem("تنبيهات", Icons.Filled.Warning)
    object Recommendations : NavItem("تقييمات", Icons.Filled.Lightbulb)
    object Settings : NavItem("إعدادات", Icons.Filled.Settings)
}

class DashboardActivity : ComponentActivity() {
    private val authManager = AuthManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        android.util.Log.d("Dashboard", "Current user: ${currentUser?.uid}")

        setContent {
            //  (RTL)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val unreadAlertsList = remember { mutableStateListOf<AlertItem>() }
                val allAlertsList = remember { mutableStateListOf<AlertItem>() }
                var selectedTab by remember { mutableStateOf<NavItem>(NavItem.Alerts) }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                // Load alerts when activity starts
                LaunchedEffect(Unit) {
                    loadAlertsFromFirestore(unreadAlertsList, allAlertsList) { error ->
                        isLoading = false
                        errorMessage = error
                    }
                }

                DashboardScreen(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    unreadAlerts = unreadAlertsList,
                    allAlerts = allAlertsList,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onMarkAsRead = { alert ->
                        unreadAlertsList.removeAll { it.documentId == alert.documentId }
                        val db = FirebaseFirestore.getInstance()
                        db.collection("alert").document(alert.documentId)
                            .update("read", true)
                            .addOnFailureListener {
                                unreadAlertsList.add(alert)
                            }
                    },
                    onRefresh = {
                        isLoading = true
                        loadAlertsFromFirestore(unreadAlertsList, allAlertsList) { error ->
                            isLoading = false
                            errorMessage = error
                        }
                    },
                    onLogout = {
                        authManager.logoutParent()
                        finish()
                    }
                )
            }
        }
    }

    private fun loadAlertsFromFirestore(
        unreadList: MutableList<AlertItem>,
        allList: MutableList<AlertItem>,
        onComplete: (String?) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            onComplete("الرجاء تسجيل الدخول أولاً")
            return
        }

        val parentId = currentUser.uid

        // STEP 1: Fetch the parent's children first (so we can map childId -> name)
        db.collection("child")
            .whereEqualTo("parentId", parentId)
            .get()
            .addOnSuccessListener { childSnapshot ->
                val childNameMap = mutableMapOf<String, String>()
                for (doc in childSnapshot.documents) {
                    val id = doc.getString("childId") ?: doc.id
                    val name = doc.getString("childName") ?: "طفل"
                    childNameMap[id] = name
                }

                // STEP 2: Now fetch the alerts
                db.collection("alert")
                    .whereEqualTo("parentId", parentId)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val allAlerts = mutableListOf<AlertItem>()
                        val unreadAlerts = mutableListOf<AlertItem>()
                        val vault = HamiSecurityVault(this)

                        for (document in snapshot.documents) {
                            val type = document.getString("type")?.lowercase() ?: ""
                            val isRead = document.getBoolean("read") ?: false
                            val timestamp = document.getTimestamp("timestamp")?.toDate()?.time
                                ?: System.currentTimeMillis()
                            val alertChildId = document.getString("childId") ?: ""

                            if (threatTypes.contains(type)) {
                                val encryptedText = document.getString("text") ?: ""
                                val decryptedText = vault.decryptAES256(encryptedText)

                                val alert = AlertItem(
                                    text = decryptedText,
                                    riskLabel = type,
                                    timestamp = timestamp,
                                    confidence = document.getDouble("confidence")?.toFloat() ?: 0f,
                                    childId = alertChildId,
                                    childName = childNameMap[alertChildId] ?: "طفل",
                                    parentId = parentId,
                                    read = isRead,
                                    context = document.getString("context") ?: "unknown",
                                    documentId = document.id
                                )
                                allAlerts.add(alert)
                                if (!isRead) unreadAlerts.add(alert)
                            }
                        }

                        allList.clear()
                        allList.addAll(allAlerts)
                        unreadList.clear()
                        unreadList.addAll(unreadAlerts)

                        onComplete(null)
                    }
                    .addOnFailureListener { e ->
                        onComplete("فشل تحميل التنبيهات: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                onComplete("فشل تحميل بيانات الأطفال: ${e.message}")
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    selectedTab: NavItem,
    onTabSelected: (NavItem) -> Unit,
    unreadAlerts: List<AlertItem>,
    allAlerts: List<AlertItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onMarkAsRead: (AlertItem) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    val navItems = listOf(NavItem.Safety, NavItem.Report, NavItem.Alerts, NavItem.Recommendations, NavItem.Settings)
    var recommendationCategory by remember { mutableStateOf<String?>(null) }
    var recommendationApp by remember { mutableStateOf<HamiApp?>(null) }
    var showRecommendationFilter by remember { mutableStateOf(false) }
    val isRecommendationInnerPage = selectedTab == NavItem.Recommendations && recommendationCategory != null


    Scaffold(
        topBar = {
            if (isRecommendationInnerPage) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (recommendationApp != null) "تفاصيل التطبيق" else recommendationCategory.orEmpty(),
                            fontFamily = AlfontDark,
                            fontSize = 20.sp,
                            color = Color(0xFF52879C)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (recommendationApp != null) recommendationApp = null
                            else recommendationCategory = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color(0xFF52879C))
                        }
                    },
                    actions = {
                        if (recommendationApp == null) {
                            IconButton(onClick = { showRecommendationFilter = true }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "تصفية التطبيقات", tint = Color(0xFF52879C))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
                )
            } else {
                TopAppBar(
                    title = { Text("حامي", fontFamily = AlfontDark, color = Color(0xFF52879C)) },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.DateRange, contentDescription = "تحديث", tint = Color(0xFF52879C))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item,
                        onClick = { onTabSelected(item) },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title, fontFamily = AlfontDark, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { paddingValues ->
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = AlfontDark)) {
            Box(modifier = Modifier.padding(paddingValues)) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF52879C))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("جاري تحميل البيانات...", fontFamily = AlfontDark, color = Color.Gray)
                            }
                        }
                    }
                    errorMessage != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("❌", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(errorMessage, fontFamily = AlfontDark, color = Color.Red)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52879C))) {
                                    Text("إعادة المحاولة", fontFamily = AlfontDark)
                                }
                            }
                        }
                    }
                    else -> {
                        when (selectedTab) {
                            NavItem.Safety -> SafetyTipsTabContent()
                            NavItem.Recommendations -> RecommendationsTabContent(
                                selectedCategory = recommendationCategory,
                                onCategorySelected = { recommendationCategory = it; recommendationApp = null; showRecommendationFilter = false },
                                selectedApp = recommendationApp,
                                onAppSelected = { recommendationApp = it },
                                showFilter = showRecommendationFilter,
                                onDismissFilter = { showRecommendationFilter = false }
                            )
                            NavItem.Alerts -> AlertsTabContent(unreadAlerts, onMarkAsRead)
                            NavItem.Report -> ReportTabContent(allAlerts)
                            NavItem.Settings -> SettingsTab(onLogout)
                        }
                    }
                }
            }
        }
    }
}

// لوحة الوعي المجتمعي - مدمجة من نسخة الفريق
// 1. Data Models (تحديث أنواع البيانات إلى Long)
data class Trend(
    val trendId: String = "",
    val region: String = "",
    val ageGroupsMostAffected: String = "",
    val cyberbullyingIncidents: Long = 0L,
    // التعديل هنا: استبدلنا التطبيقات الخطرة القديمة بالتطبيقات المتكررة الجديدة
    val topApps: List<AppTrendCount> = emptyList(),
    val topWords: List<TopWord> = emptyList()
)
data class RiskyApp(
    val appName: String = "",
    val riskFactor: String = "",
    val commonThreat: String = ""
)
data class AppTrendCount(
    val name: String = "",
    val count: Int = 0
)
data class TopWord(
    val word: String = "",
    val count: Int = 0
)

@Composable
fun SafetyTipsTabContent() {
    val hamiTeal = Color(0xFF52879C)
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
    )

    var trendData by remember { mutableStateOf<Trend?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("trend")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    trendData = snapshot.documents[0].toObject(Trend::class.java)
                }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = hamiTeal,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (trendData != null) {
            TrendDashboard(trend = trendData!!, hamiTeal = hamiTeal)
        } else {
            Text(
                text = "لا توجد بيانات حالياً",
                fontSize = 18.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun AnimatedIncidentCounter(targetCount: Long) {
    var animationPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val animatedValue by animateIntAsState(
        targetValue = if (animationPlayed) targetCount.toInt() else 0,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "NumberAnimation"
    )

    Text(
        text = "إجمالي الحوادث المسجلة: $animatedValue",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFD32F2F)
    )
}

@Composable
fun TrendDashboard(trend: Trend, hamiTeal: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.padding(bottom = 8.dp)) { HamiPageTitle("الوعي المجتمعي") }

        DashboardCard(title = "الكلمات السلبية الأكثر رصداً في حامي", hamiTeal = hamiTeal) {
            if (trend.topWords.isEmpty()) {
                HamiNote("لا توجد بيانات كافية حالياً")
            } else {
                trend.topWords.take(5).forEachIndexed { index, word ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ترتيب الرقم والكلمة
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}", // رقم الكلمة (1, 2, 3...)
                                color = Color.Gray,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = word.word, fontFamily = AlfontDark, fontWeight = FontWeight.Bold, color = Color.DarkGray) // افترض أن اسم المتغير داخل الكلاس هو word
                        }

                        // عدد المرات
                        Text(
                            text = "${word.count} مرات",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = AlfontDark
                        )
                    }
                }
            }
        }

        DashboardCard(title = "المنصات الأعلى تسجيلاً للتنبيهات", hamiTeal = hamiTeal) {
            trend.topApps.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = app.name, fontFamily = AlfontDark, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Text(
                        text = "${app.count} تنبيهات",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = AlfontDark
                    )
                }
            }
        }

        DashboardCard(
            title = "إحصائيات مجتمع حامي",
            hamiTeal = hamiTeal
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. حساب المتوسط اليومي
                val dailyAverage = if (trend.cyberbullyingIncidents > 0) trend.cyberbullyingIncidents / 7 else 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المتوسط اليومي: $dailyAverage تنبيه/يوم",
                        color = Color.DarkGray,
                        fontSize = 14.sp,
                        fontFamily = AlfontDark
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // أيقونة الـ i
                    androidx.compose.material.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Info,
                        contentDescription = "معلومات الحساب",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                android.widget.Toast.makeText(
                                    context,
                                    "يتم الحساب بقسمة إجمالي التنبيهات المسجلة على 7 أيام",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                    )
                }

                // 2. تحديد مستوى الخطر برمجياً (0 = منخفض، 1 = متوسط، 2 = مرتفع)
                val riskLevel = when {
                    trend.cyberbullyingIncidents >= 50 -> 2
                    trend.cyberbullyingIncidents in 20..49 -> 1
                    else -> 0
                }

                Text(
                    text = "مؤشر الخطر المجتمعي",
                    color = Color(0xFF52879C),
                    fontSize = 16.sp,
                    fontFamily = AlfontDark,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 3. رسم شكل المقياس (Scale)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    // الشريط الملون
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                    ) {
                        // قسم "منخفض"
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (riskLevel == 0) Color(0xFF388E3C) else Color(0xFFE0E0E0)))
                        Spacer(modifier = Modifier.width(4.dp))
                        // قسم "متوسط"
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (riskLevel == 1) Color(0xFFF57C00) else Color(0xFFE0E0E0)))
                        Spacer(modifier = Modifier.width(4.dp))
                        // قسم "مرتفع"
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (riskLevel == 2) Color.Red else Color(0xFFE0E0E0)))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // الكلمات تحت المقياس
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "منخفض", fontSize = 12.sp, fontWeight = if (riskLevel == 0) FontWeight.Bold else FontWeight.Normal, color = if (riskLevel == 0) Color(0xFF388E3C) else Color.Gray)
                        Text(text = "متوسط", fontSize = 12.sp, fontWeight = if (riskLevel == 1) FontWeight.Bold else FontWeight.Normal, color = if (riskLevel == 1) Color(0xFFF57C00) else Color.Gray)
                        Text(text = "مرتفع", fontSize = 12.sp, fontWeight = if (riskLevel == 2) FontWeight.Bold else FontWeight.Normal, color = if (riskLevel == 2) Color.Red else Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "إجمالي التنبيهات (آخر 7 أيام): ${trend.cyberbullyingIncidents}",
                    color = Color.DarkGray,
                    fontFamily = AlfontDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun DashboardCard(title: String, hamiTeal: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.padding(bottom = 16.dp)) { HamiSectionTitle(title) }
            content()
        }
    }
}
@Composable
fun TopWordRow(topWord: TopWord, hamiTeal: Color, rank: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // دائرة صغيرة لرقم الترتيب (1, 2, 3..)
            Surface(
                shape = CircleShape,
                color = hamiTeal.copy(alpha = 0.1f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "$rank", color = hamiTeal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

            // الكلمة نفسها
            Text(
                text = topWord.word,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
        }

        // عدد المرات
        Text(
            text = "${topWord.count} مرات",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

// تحديث الواجهة وتضمين الأيقونات والشارات لمستوى الخطر
@Composable
fun RiskyAppRow(app: RiskyApp) {
    val riskColor = when(app.riskFactor.lowercase()) {
        "high" -> Color(0xFFD32F2F)
        "medium" -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }
    val translatedRisk = when(app.riskFactor.lowercase()) {
        "high" -> "عالي"
        "medium" -> "متوسط"
        else -> "منخفض"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = "تحذير",
            tint = riskColor,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(28.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.appName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = "التهديد الشائع: ${app.commonThreat}", fontSize = 12.sp, color = Color.Gray)
        }

        Surface(
            color = riskColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, riskColor.copy(alpha = 0.3f))
        ) {
            Text(
                text = translatedRisk,
                color = riskColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun AlertsTabContent(
    alerts: List<AlertItem>,
    onMarkAsRead: (AlertItem) -> Unit
) {
    val hamiTeal = Color(0xFF52879C)
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
    )

    // Group alerts by childId (preserving childName)
    val grouped = remember(alerts) {
        alerts
            .groupBy { it.childId }
            .mapValues { (_, list) -> list.sortedByDescending { it.timestamp } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(16.dp)
    ) {
        Text(
            "التنبيهات الجديدة",
            fontSize = 28.sp,
            fontFamily = AlfontDark,
            color = hamiTeal
        )
        Spacer(modifier = Modifier.height(8.dp))
        HamiNote("اسحب التنبيه لتحديد كمقروء")
        Spacer(modifier = Modifier.height(16.dp))

        if (alerts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("لا توجد تنبيهات جديدة", color = Color(0xFF788B94), fontFamily = AlfontDark)
                }
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            grouped.forEach { (childId, childAlerts) ->
                val childName = childAlerts.firstOrNull()?.childName ?: "طفل"

                // ===== Child section header =====
                item(key = "header_$childId") {
                    ChildHeaderBlock(
                        childName = childName,
                        count = childAlerts.size,
                        color = hamiTeal
                    )
                }

                // ===== This child's alerts =====
                items(childAlerts, key = { it.documentId }) { alert ->
                    SwipeToMarkReadCard(
                        alert = alert,
                        onMarkAsRead = { onMarkAsRead(alert) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChildHeaderBlock(childName: String, count: Int, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👤", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = childName,
                    fontSize = 16.sp,
                    fontFamily = AlfontDark,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Surface(
                color = color,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "$count",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = AlfontDark,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToMarkReadCard(alert: AlertItem, onMarkAsRead: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onMarkAsRead()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                Color(0xFF4CAF50) else Color.Transparent
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd // لأن الواجهة RTL، هذي بتصير باليمين
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تمت المراجعة", color = Color.White, fontSize = 14.sp, fontFamily = AlfontDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.Done, contentDescription = "Mark as Read", tint = Color.White)
                }
            }
        },
        enableDismissFromStartToEnd = false,
        content = {
            AlertCard(alert = alert)
        }
    )
}

@Composable
fun AlertCard(alert: AlertItem) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPref = remember {
        context.getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
    }
    val showAlertText = remember {
        sharedPref.getBoolean("SHOW_ALERT_TEXT", false)
    }

    val sdf = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")) }
    val riskColor = threatColors[alert.riskLabel] ?: Color(0xFF64B5F6)
    val displayName = threatDisplayNames[alert.riskLabel] ?: alert.riskLabel

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ===== Header row: risk label + child name + timestamp =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Risk type badge
                    Surface(color = riskColor.copy(alpha = 0.1f)) {
                        Text(
                            displayName,
                            color = riskColor,
                            fontSize = 12.sp,
                            fontFamily = AlfontDark,
                            modifier = Modifier.padding(4.dp)
                        )
                    }

                    // Child name badge (only if present)
                    if (alert.childName.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👤", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                alert.childName,
                                color = Color(0xFF788B94),
                                fontSize = 12.sp,
                                fontFamily = AlfontDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Timestamp
                Text(
                    sdf.format(Date(alert.timestamp)),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = AlfontDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ===== Body: alert text (hidden by default) =====
            if (showAlertText) {
                Text(
                    text = alert.text,
                    fontSize = 14.sp,
                    fontFamily = AlfontDark
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "النص مخفي - فعّل الإظهار من الإعدادات",
                        fontSize = 13.sp,
                        fontFamily = AlfontDark,
                        color = Color.Gray
                    )
                }
            }

            // ===== Optional footer: context chip =====
            if (alert.context.isNotEmpty() && alert.context != "unknown") {
                Spacer(modifier = Modifier.height(8.dp))
                val contextLabel = when (alert.context) {
                    "keyboard_input" -> "⌨️ لوحة المفاتيح"
                    "notification" -> "🔔 إشعار"
                    "screen_reading" -> "📱 قراءة الشاشة"
                    else -> alert.context
                }
                Text(
                    text = contextLabel,
                    fontSize = 11.sp,
                    fontFamily = AlfontDark,
                    color = Color(0xFFB0BEC5)
                )
            }
        }
    }
}

@Composable
fun ReportTabContent(allAlerts: List<AlertItem>) {
    val hamiTeal = Color(0xFF52879C)
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
    )

    val weeks = remember(allAlerts) { getAvailableWeeks(allAlerts) }
    var selectedWeekIndex by remember { mutableStateOf(0) }

    LaunchedEffect(weeks.size) {
        if (selectedWeekIndex >= weeks.size && weeks.isNotEmpty()) {
            selectedWeekIndex = 0
        }
    }

    val currentWeek = if (weeks.isNotEmpty() && selectedWeekIndex < weeks.size) {
        weeks[selectedWeekIndex]
    } else null

    val weekAlerts = currentWeek?.let { week ->
        allAlerts.filter { alert ->
            val date = Date(alert.timestamp)
            getWeekNumber(date) == week.weekNumber && getYear(date) == week.year
        }
    } ?: emptyList()

    val typeCounts = threatTypes.associateWith { type ->
        weekAlerts.count { it.riskLabel.lowercase() == type }
    }
    val maxTypeCount = typeCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    val dayCounts = MutableList(7) { 0 }
    weekAlerts.forEach { alert ->
        val calendar = Calendar.getInstance()
        calendar.time = Date(alert.timestamp)
        val dayIndex = dayOfWeekMapping[calendar.get(Calendar.DAY_OF_WEEK)] ?: 0
        dayCounts[dayIndex] = dayCounts[dayIndex] + 1
    }
    val maxDayCount = dayCounts.maxOrNull()?.coerceAtLeast(1) ?: 1

    val weeklyTotals = weeks.take(6).map { week ->
        val count = allAlerts.count { alert ->
            val date = Date(alert.timestamp)
            getWeekNumber(date) == week.weekNumber && getYear(date) == week.year
        }
        Pair(week.displayNameShort, count)
    }
    val maxWeeklyCount = weeklyTotals.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HamiPageTitle("التقارير الأسبوعية")
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HamiSectionTitle("📅 اختيار الأسبوع")
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (selectedWeekIndex < weeks.size - 1) selectedWeekIndex++ },
                            enabled = weeks.isNotEmpty() && selectedWeekIndex < weeks.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "السابق", tint = hamiTeal)
                        }

                        Text(
                            text = currentWeek?.displayName ?: "لا توجد بيانات",
                            fontSize = 16.sp,
                            fontFamily = AlfontDark,
                            color = hamiTeal,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = { if (selectedWeekIndex > 0) selectedWeekIndex-- },
                            enabled = weeks.isNotEmpty() && selectedWeekIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "التالي", tint = hamiTeal)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HamiSectionTitle("📊 أنواع التهديدات")
                    Spacer(modifier = Modifier.height(16.dp))

                    threatTypes.forEach { type ->
                        val count = typeCounts[type] ?: 0
                        val color = threatColors[type] ?: Color.Gray
                        val displayName = threatDisplayNames[type] ?: type
                        BarChartRow(displayName, count, maxTypeCount, color)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HamiSectionTitle("📊 التوزيع اليومي")
                    Spacer(modifier = Modifier.height(16.dp))

                    daysOfWeek.forEachIndexed { index, day ->
                        val count = dayCounts[index]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(day, fontSize = 11.sp, modifier = Modifier.width(70.dp), fontFamily = AlfontDark, color = Color.Gray)
                            Box(modifier = Modifier.weight(1f).height(24.dp).background(Color(0xFFE0E0E0))) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(if (maxDayCount > 0) count.toFloat() / maxDayCount else 0f).background(hamiTeal))
                            }
                            Text("$count", fontSize = 12.sp, modifier = Modifier.width(40.dp).padding(start = 10.dp), fontFamily = AlfontDark, color = hamiTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (weeklyTotals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        HamiSectionTitle("📈 التوزيع الأسبوعي")
                        Spacer(modifier = Modifier.height(16.dp))

                        weeklyTotals.forEach { (weekName, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(weekName, fontSize = 11.sp, modifier = Modifier.width(70.dp), fontFamily = AlfontDark, color = Color.Gray)
                                Box(modifier = Modifier.weight(1f).height(24.dp).background(Color(0xFFE0E0E0))) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / maxWeeklyCount).background(hamiTeal))
                                }
                                Text("$count", fontSize = 12.sp, modifier = Modifier.width(40.dp).padding(start = 10.dp), fontFamily = AlfontDark, color = hamiTeal, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HamiSectionTitle("📋 ملخص الأسبوع")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${weekAlerts.size}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = hamiTeal)
                            Text("إجمالي التهديدات", fontSize = 12.sp, color = Color.Gray, fontFamily = AlfontDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BarChartRow(label: String, count: Int, maxCount: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(90.dp), fontFamily = AlfontDark, color = color, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.weight(1f).height(24.dp).background(Color(0xFFE0E0E0))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / maxCount).background(color))
        }
        Text("$count", fontSize = 12.sp, modifier = Modifier.width(40.dp).padding(start = 10.dp), fontFamily = AlfontDark, color = color, fontWeight = FontWeight.Bold)
    }
}

fun getAvailableWeeks(alerts: List<AlertItem>): List<WeekData> {
    val weekMap = mutableMapOf<String, WeekData>()
    alerts.forEach { alert ->
        val date = Date(alert.timestamp)
        val weekNumber = getWeekNumber(date)
        val year = getYear(date)
        val key = "${year}_${weekNumber}"
        if (!weekMap.containsKey(key)) {
            weekMap[key] = WeekData(weekNumber, year, "الأسبوع $weekNumber ($year)", "أ$weekNumber")
        }
    }
    return weekMap.values.sortedByDescending { it.year * 100 + it.weekNumber }
}

fun getWeekNumber(date: Date): Int {
    val calendar = Calendar.getInstance()
    calendar.time = date
    return calendar.get(Calendar.WEEK_OF_YEAR)
}

fun getYear(date: Date): Int {
    val calendar = Calendar.getInstance()
    calendar.time = date
    return calendar.get(Calendar.YEAR)
}

data class WeekData(val weekNumber: Int, val year: Int, val displayName: String, val displayNameShort: String)

@Composable
fun SettingsTab(onLogout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPref = remember {
        context.getSharedPreferences("HamiPrefs", android.content.Context.MODE_PRIVATE)
    }

    // Default is FALSE (don't show text)
    var showAlertText by remember {
        mutableStateOf(sharedPref.getBoolean("SHOW_ALERT_TEXT", false))
    }

    val backgroundGradient = Brush.linearGradient(colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF)))
    val hamiTeal = Color(0xFF52879C)

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
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                HamiPageTitle("الإعدادات")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "إظهار نص التنبيه",
                            fontSize = 18.sp,
                            fontFamily = AlfontDark,
                            color = hamiTeal,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "عند التفعيل، سيظهر النص الكامل للرسالة في التنبيهات",
                            fontSize = 12.sp,
                            fontFamily = AlfontDark,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = showAlertText,
                        onCheckedChange = { newValue ->
                            showAlertText = newValue
                            sharedPref.edit().putBoolean("SHOW_ALERT_TEXT", newValue).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = hamiTeal
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Logout button
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp).shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
            ) {
                Text("تسجيل الخروج", fontFamily = AlfontDark, color = Color.White, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// App safety data comes from the two independent Firestore collections.
private data class HamiRisk(val enabled: Boolean = false, val description: String = "")
private data class HamiApp(
    val id: String, val name: String, val category: String, val ageMin: Int,
    val description: String, val order: Int, val risks: Map<String, HamiRisk>
) {
    val riskCount: Int get() = risks.values.count { it.enabled }
}
private data class HamiControl(
    val toolName: String, val toolDescription: String, val steps: List<String>
)
private val hamiRiskNames = linkedMapOf(
    "textVoiceChat" to "المحادثات النصية والصوتية",
    "inappropriateContent" to "محتوى غير ملائم",
    "personalInfoSharing" to "مشاركة المعلومات الشخصية",
    "inAppPurchases" to "عمليات شراء داخل التطبيق"
)
private val hamiCategories = linkedMapOf(
    "الألعاب" to "games",
    "التواصل الاجتماعي والمراسلة" to "communication",
    "الترفيه والوسائط" to "entertainment"
)
private val hamiBlue = Color(0xFF52879C)
private val hamiBackground = Brush.linearGradient(listOf(Color.White, Color(0xFFC0D4DF)))

private fun hamiRiskColor(count: Int): Color = when (count) {
    0 -> Color(0xFF2E7D32)
    1 -> Color(0xFF81C784)
    2 -> Color(0xFFFBC02D)
    3 -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}

// Look up local app icons by their stable Firestore ID. Missing icons use a neutral placeholder.
@Composable
private fun HamiAppLogo(app: HamiApp, size: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawableName = when (app.id) {
        "hello_kitty" -> "hello_kitty_and_friends_world"
        else -> app.id
    }
    val drawableId = remember(drawableName) {
        context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    }
    if (drawableId != 0) {
        Image(
            painter = painterResource(drawableId),
            contentDescription = app.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp))
        )
    } else {
        Surface(
            modifier = Modifier.size(size), shape = RoundedCornerShape(12.dp),
            color = Color(0xFFE6EFF3)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = hamiBlue)
            }
        }
    }
}

@Composable
private fun HamiRiskCircle(count: Int, size: androidx.compose.ui.unit.Dp = 39.dp) {
    Surface(shape = CircleShape, color = hamiRiskColor(count), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text("$count", color = Color.White,
                fontSize = if (size < 30.dp) 12.sp else 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun hamiAgeLabel(age: Int): String = "مناسب لعمر $age ${if (age >= 11) "سنة" else "سنوات"} فأكثر"

@Composable
private fun RecommendationsTabContent(
    selectedCategory: String?, onCategorySelected: (String?) -> Unit,
    selectedApp: HamiApp?, onAppSelected: (HamiApp?) -> Unit,
    showFilter: Boolean, onDismissFilter: () -> Unit
) {
    var apps by remember { mutableStateOf<List<HamiApp>>(emptyList()) }
    var controls by remember { mutableStateOf<Map<String, HamiControl>>(emptyMap()) }
    var appsLoaded by remember { mutableStateOf(false) }
    var controlsLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedAgeRange by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var excludedRisks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var onlyControlled by remember { mutableStateOf(false) }
    LaunchedEffect(selectedCategory) {
        selectedAgeRange = null
        excludedRisks = emptySet()
        onlyControlled = false
    }

    DisposableEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        val appsListener = db.collection("apps").addSnapshotListener { snapshot, error ->
            if (error != null) {
                loadError = "تعذر تحميل بيانات التطبيقات: ${error.localizedMessage}"
                appsLoaded = true
            } else if (snapshot != null) {
                apps = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val rawRisks = data["risks"] as? Map<*, *> ?: emptyMap<Any, Any>()
                    val descriptions = data["riskDescriptions"] as? Map<*, *> ?: emptyMap<Any, Any>()
                    val risks = hamiRiskNames.keys.associateWith { key ->
                        HamiRisk(
                            enabled = rawRisks[key] as? Boolean ?: false,
                            description = descriptions[key] as? String ?: ""
                        )
                    }
                    HamiApp(
                        id = doc.id,
                        name = (data["name"] as? String).orEmpty(),
                        category = (data["category"] as? String).orEmpty(),
                        ageMin = (data["ageMin"] as? Number)?.toInt() ?: 0,
                        description = (data["description"] as? String).orEmpty(),
                        order = (data["displayOrder"] as? Number)?.toInt() ?: 0,
                        risks = risks
                    )
                }.sortedWith(compareBy<HamiApp> { it.order }.thenBy { it.name })
                appsLoaded = true
                loadError = null
            }
        }
        val controlListener = db.collection("parental_controls").addSnapshotListener { snapshot, error ->
            if (error != null) {
                loadError = "تعذر تحميل أدلة الرقابة الأبوية: ${error.localizedMessage}"
                controlsLoaded = true
            } else if (snapshot != null) {
                controls = snapshot.documents.associate { doc ->
                    val data = doc.data.orEmpty()
                    doc.id to HamiControl(
                        toolName = (data["toolName"] as? String).orEmpty(),
                        toolDescription = (data["toolDescription"] as? String).orEmpty(),
                        steps = (data["steps"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                    )
                }
                controlsLoaded = true
                loadError = null
            }
        }
        onDispose { appsListener.remove(); controlListener.remove() }
    }

    if (selectedApp != null) {
        val latest = apps.firstOrNull { it.id == selectedApp.id } ?: selectedApp
        HamiAppDetails(latest, controls[latest.id])
        return
    }
    if (selectedCategory == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(hamiBackground).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HamiPageTitle("تقييم أمان التطبيقات")
            HamiNote("تعرّف على المخاطر المحتملة في التطبيقات وطرق الرقابة الأبوية المتاحة")
            GuideCategoryCard("الألعاب", Icons.Filled.SportsEsports, Modifier.weight(1f)) { onCategorySelected("الألعاب") }
            GuideCategoryCard("التواصل الاجتماعي والمراسلة", Icons.Filled.People, Modifier.weight(1f)) { onCategorySelected("التواصل الاجتماعي والمراسلة") }
            GuideCategoryCard("الترفيه والوسائط", Icons.Filled.PlayCircle, Modifier.weight(1f)) { onCategorySelected("الترفيه والوسائط") }
        }
        return
    }
    val appsInCategory = apps.filter { it.category == hamiCategories[selectedCategory] }
    val minAppAge = appsInCategory.minOfOrNull { it.ageMin } ?: 6
    val maxAppAge = appsInCategory.maxOfOrNull { it.ageMin } ?: 12
    val ageBounds = minAppAge.toFloat()..maxAppAge.coerceAtLeast(minAppAge + 1).toFloat()
    val effectiveAgeRange = selectedAgeRange?.let { range ->
        range.start.coerceIn(ageBounds.start, ageBounds.endInclusive)..
                range.endInclusive.coerceIn(ageBounds.start, ageBounds.endInclusive)
    } ?: ageBounds
    val categoryApps = appsInCategory.filter { app ->
        (app.ageMin.toFloat() in effectiveAgeRange) &&
                excludedRisks.all { key -> app.risks[key]?.enabled != true } &&
                (!onlyControlled || controls.containsKey(app.id))
    }
    // Apply filters only after confirmation; do not persist them.
    if (showFilter) {
        var draftAgeRange by remember(showFilter) { mutableStateOf(effectiveAgeRange) }
        var draftRisks by remember(showFilter) { mutableStateOf(excludedRisks) }
        var draftControlled by remember(showFilter) { mutableStateOf(onlyControlled) }

        AlertDialog(
            onDismissRequest = onDismissFilter,
            title = { HamiSectionTitle("تصفية التطبيقات", 20.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HamiSectionTitle("الفئة العمرية", 16.sp)
                    Text("من ${draftAgeRange.start.toInt()} إلى ${draftAgeRange.endInclusive.toInt()} سنة", fontFamily = AlfontDark, color = hamiBlue)
                    RangeSlider(
                        value = draftAgeRange,
                        onValueChange = { draftAgeRange = it },
                        valueRange = ageBounds,
                        steps = (ageBounds.endInclusive - ageBounds.start).toInt().minus(1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(thumbColor = hamiBlue, activeTrackColor = hamiBlue)
                    )
                    HorizontalDivider(color = Color(0xFFE0E0E0))
                    HamiSectionTitle("استبعاد التطبيقات التي تتضمن", 16.sp)
                    hamiRiskNames.forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                draftRisks = if (key in draftRisks) draftRisks - key else draftRisks + key
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = key in draftRisks,
                                onCheckedChange = { checked ->
                                    draftRisks = if (checked) draftRisks + key else draftRisks - key
                                }
                            )
                            Text(label, fontFamily = AlfontDark, fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE0E0E0))
                    HamiSectionTitle("الرقابة الأبوية", 16.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { draftControlled = !draftControlled },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = draftControlled, onCheckedChange = { draftControlled = it })
                        Text("عرض التطبيقات التي توفر رقابة أبوية فقط", fontFamily = AlfontDark, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedAgeRange = draftAgeRange
                    excludedRisks = draftRisks
                    onlyControlled = draftControlled
                    onDismissFilter()
                }) { Text("تم", fontFamily = AlfontDark, color = hamiBlue) }
            },
            dismissButton = {
                TextButton(onClick = onDismissFilter) { Text("إلغاء", fontFamily = AlfontDark) }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(hamiBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        when {
            !appsLoaded || !controlsLoaded -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = hamiBlue) } }
            loadError != null -> item { Text(loadError.orEmpty(), color = Color.Red, fontFamily = AlfontDark) }
            categoryApps.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "لا توجد تطبيقات تطابق الفلاتر المختارة",
                        fontFamily = AlfontDark,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> items(categoryApps, key = { it.id }) { app ->
                Card(onClick = { onAppSelected(app) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HamiAppLogo(app, 50.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                HamiSectionTitle(app.name, 17.sp)
                                if (controls.containsKey(app.id)) {
                                    Icon(Icons.Filled.Shield, contentDescription = "تتوفر رقابة أبوية",
                                        tint = hamiBlue, modifier = Modifier.size(19.dp))
                                }
                            }
                            Text(hamiAgeLabel(app.ageMin), fontSize = 12.sp, color = Color.Gray)
                        }
                        HamiRiskCircle(app.riskCount, 28.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideCategoryCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = hamiBlue, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(10.dp))
            HamiSectionTitle(title, 19.sp)
        }
    }
}

@Composable
private fun HamiAppDetails(app: HamiApp, control: HamiControl?) {
    var expanded by remember(app.id) { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize().background(hamiBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HamiAppLogo(app, 76.dp)
                        Column(Modifier.weight(1f)) {
                            HamiSectionTitle(app.name, 21.sp)
                            Text(hamiAgeLabel(app.ageMin), fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Text(app.description, fontFamily = AlfontDark, fontSize = 14.sp,
                        lineHeight = 22.sp, color = Color.DarkGray)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        HamiSectionTitle("المخاطر المحتملة")
                        HamiRiskCircle(app.riskCount, 24.dp)
                    }
                    hamiRiskNames.forEach { (key, label) ->
                        val risk = app.risks[key] ?: HamiRisk()
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (risk.enabled) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = if (risk.enabled) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, fontFamily = AlfontDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (risk.description.isNotBlank()) {
                                    Text(risk.description, fontFamily = AlfontDark, color = Color.DarkGray,
                                        fontSize = 13.sp, lineHeight = 21.sp)
                                }
                            }
                        }
                    }
                    if (control != null) {
                        HorizontalDivider(color = Color(0xFFE0E0E0))
                        HamiSectionTitle("الرقابة الأبوية")
                        val tool = control.toolName.removePrefix("الرقابة الأبوية: ").trim()
                        Text("${tool}: ${control.toolDescription}", fontFamily = AlfontDark,
                            color = Color.DarkGray, fontSize = 14.sp, lineHeight = 22.sp)
                        Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE6EFF3))) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("خطوات التفعيل", fontFamily = AlfontDark, color = hamiBlue,
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "إخفاء الخطوات" else "إظهار الخطوات", tint = hamiBlue)
                            }
                        }
                        AnimatedVisibility(visible = expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                control.steps.forEachIndexed { index, step ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("${index + 1}.", fontFamily = AlfontDark, color = hamiBlue, fontSize = 14.sp)
                                        Text(step, fontFamily = AlfontDark, color = Color.DarkGray,
                                            fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}