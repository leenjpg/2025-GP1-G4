package com.example.aimoduel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))

sealed class NavItem(val title: String, val icon: ImageVector) {
    object Safety : NavItem("نصائح", Icons.Filled.Info)
    object Report : NavItem("تقرير", Icons.Filled.DateRange)
    object Alerts : NavItem("تنبيهات", Icons.Filled.Warning)
    object AddChild : NavItem("إضافة", Icons.Filled.Add)
    object Settings : NavItem("إعدادات", Icons.Filled.Settings)
}

class DashboardActivity : ComponentActivity() {
    private val authManager = AuthManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val vault = remember { HamiSecurityVault(context) }
            val alertsList = remember { mutableStateListOf<AlertItem>().apply { addAll(vault.getAlertsList()) } }
            var selectedTab by remember { mutableStateOf<NavItem>(NavItem.Alerts) }

            DashboardScreen(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                alerts = alertsList,
                onDeleteAlert = { alert ->
                    vault.deleteAlert(alert.timestamp)
                    alertsList.remove(alert)
                },
                onLogout = {
                    authManager.logoutParent()
                    finish()
                },
                vault = vault
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    selectedTab: NavItem,
    onTabSelected: (NavItem) -> Unit,
    alerts: MutableList<AlertItem>,
    onDeleteAlert: (AlertItem) -> Unit,
    onLogout: () -> Unit,
    vault: HamiSecurityVault
) {
    val navItems = listOf(NavItem.Safety, NavItem.Report, NavItem.Alerts, NavItem.AddChild, NavItem.Settings)

    Scaffold(
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
            when (selectedTab) {
                NavItem.Alerts -> AlertsTabContent(alerts, onDeleteAlert, vault)
                NavItem.Settings -> SettingsTab(onLogout)
                else -> PlaceholderTab(selectedTab.title, "قريباً في تحديث حامي القادم")
            }
        }
    }
}

@Composable
fun AlertsTabContent(alerts: MutableList<AlertItem>, onDeleteAlert: (AlertItem) -> Unit, vault: HamiSecurityVault) {
    val hamiTeal = Color(0xFF52879C)
    val backgroundGradient = Brush.linearGradient(colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0D4DF)))

    Column(modifier = Modifier.fillMaxSize().background(backgroundGradient).padding(16.dp)) {
        Text("ملخص النشاط", style = MaterialTheme.typography.headlineSmall, fontFamily = AlfontDark, color = hamiTeal)

        TextButton(onClick = {
            val mockAlert = AlertItem(
                text = "محتوى تجريبي للاختبار",
                riskLabel = "Offensive",
                timestamp = System.currentTimeMillis()
            )
            vault.saveAlert(mockAlert)
            alerts.clear()
            alerts.addAll(vault.getAlertsList())
        }) {
            Text("➕ إضافة بيانات تجريبية", fontSize = 12.sp, color = hamiTeal, fontFamily = AlfontDark)
        }

        if (alerts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد مخاطر مكتشفة حالياً", color = Color(0xFF788B94), fontFamily = AlfontDark)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = alerts, key = { it.timestamp }) { alert ->
                    SwipeToDeleteContainer(item = alert, onDelete = { onDeleteAlert(alert) }) {
                        AlertCard(alert)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(item: AlertItem, onDelete: (AlertItem) -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item)
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Color.Red else Color.Transparent
            Box(modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
        enableDismissFromStartToEnd = false,
        content = { content() }
    )
}

@Composable
fun AlertCard(alert: AlertItem) {
    val sdf = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(color = Color(0xFFFFEBEE)) {
                    Text(alert.riskLabel, color = Color(0xFFD32F2F), fontSize = 12.sp, fontFamily = AlfontDark, modifier = Modifier.padding(4.dp))
                }
                Text(sdf.format(Date(alert.timestamp)), color = Color.Gray, fontSize = 12.sp, fontFamily = AlfontDark)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(alert.text, fontSize = 16.sp, fontFamily = AlfontDark)
        }
    }
}

@Composable
fun PlaceholderTab(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, fontSize = 24.sp, color = Color(0xFF52879C), fontFamily = AlfontDark)
        Text(subtitle, fontSize = 16.sp, color = Color.Gray, fontFamily = AlfontDark)
    }
}

@Composable
fun SettingsTab(onLogout: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text("خروج", fontFamily = AlfontDark)
        }
    }
}
