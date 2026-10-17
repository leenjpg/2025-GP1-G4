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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))

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
    object Safety : NavItem("نصائح", Icons.Filled.Info)
    object Report : NavItem("تقرير", Icons.Filled.DateRange)
    object Alerts : NavItem("تنبيهات", Icons.Filled.Warning)
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
    val navItems = listOf(NavItem.Safety, NavItem.Report, NavItem.Alerts, NavItem.Settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حامي", fontFamily = AlfontDark, color = Color(0xFF52879C)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.DateRange, contentDescription = "تحديث", tint = Color(0xFF52879C))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
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
                        NavItem.Alerts -> AlertsTabContent(unreadAlerts, onMarkAsRead)
                        NavItem.Report -> ReportTabContent(allAlerts)
                        NavItem.Settings -> SettingsTab(onLogout)
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyTipsTabContent() {
    val hamiTeal = Color(0xFF52879C)
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📚", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("قريباً", fontSize = 24.sp, fontFamily = AlfontDark, color = hamiTeal)
            Text("نصائح التوعية الرقمية", fontSize = 14.sp, fontFamily = AlfontDark, color = Color.Gray)
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
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = AlfontDark,
            color = hamiTeal
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "اسحب التنبيه لتحديد كمقروء",
            fontSize = 12.sp,
            color = Color(0xFF788B94),
            fontFamily = AlfontDark
        )
        Spacer(modifier = Modifier.height(12.dp))

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
        shape = RectangleShape,
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
                shape = RectangleShape
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
        shape = RectangleShape,
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📅 اختيار الأسبوع", fontSize = 16.sp, fontFamily = AlfontDark, color = hamiTeal)
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
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 أنواع التهديدات", fontSize = 18.sp, fontFamily = AlfontDark, color = hamiTeal)
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
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 التوزيع اليومي", fontSize = 18.sp, fontFamily = AlfontDark, color = hamiTeal)
                    Spacer(modifier = Modifier.height(16.dp))

                    daysOfWeek.forEachIndexed { index, day ->
                        val count = dayCounts[index]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(day, fontSize = 11.sp, modifier = Modifier.width(70.dp), fontFamily = AlfontDark, color = Color.Gray)
                            Box(modifier = Modifier.weight(1f).height(24.dp).background(Color(0xFFE0E0E0))) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(if (maxDayCount > 0) count.toFloat() / maxDayCount else 0f).background(hamiTeal))
                            }
                            Text("$count", fontSize = 12.sp, modifier = Modifier.width(40.dp), fontFamily = AlfontDark, color = hamiTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (weeklyTotals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📈 التوزيع الأسبوعي", fontSize = 18.sp, fontFamily = AlfontDark, color = hamiTeal)
                        Spacer(modifier = Modifier.height(16.dp))

                        weeklyTotals.forEach { (weekName, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(weekName, fontSize = 11.sp, modifier = Modifier.width(70.dp), fontFamily = AlfontDark, color = Color.Gray)
                                Box(modifier = Modifier.weight(1f).height(24.dp).background(Color(0xFFE0E0E0))) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / maxWeeklyCount).background(hamiTeal))
                                }
                                Text("$count", fontSize = 12.sp, modifier = Modifier.width(40.dp), fontFamily = AlfontDark, color = hamiTeal, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📋 ملخص الأسبوع", fontSize = 18.sp, fontFamily = AlfontDark, color = hamiTeal)
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
        Text("$count", fontSize = 12.sp, modifier = Modifier.width(40.dp), fontFamily = AlfontDark, color = color, fontWeight = FontWeight.Bold)
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
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "الإعدادات",
                fontSize = 28.sp,
                fontFamily = AlfontDark,
                color = hamiTeal
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Privacy Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            fontSize = 16.sp,
                            fontFamily = AlfontDark,
                            color = hamiTeal,
                            fontWeight = FontWeight.Bold
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
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("تسجيل الخروج", fontFamily = AlfontDark, color = Color.White, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
