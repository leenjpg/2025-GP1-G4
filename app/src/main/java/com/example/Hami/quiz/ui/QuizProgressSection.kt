package com.example.Hami.quiz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.School
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.Hami.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val quizTeal = Color(0xFF52879C)

private val quizFont = FontFamily(
    Font(R.font.alfont_com_dark)
)

private data class WeeklyQuizProgress(
    val week: String,
    val status: String,
    val category: String,
    val completedAt: Date?
)

private data class ChildQuizReport(
    val childId: String,
    val childName: String,
    val progress: List<WeeklyQuizProgress>
)

/**
 * Parent dashboard component.
 *
 * Displays the educational quiz progress
 * for all children linked to the parent.
 */
@Composable
fun QuizProgressSection(
    selectedWeekStart: String
) {

    val parentId = remember {
        FirebaseAuth.getInstance().currentUser?.uid
    }

    var children by remember {
        mutableStateOf<List<ChildQuizReport>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    /**
     * Listen for changes in children's Firestore records.
     * This updates the parent's report after quiz completion.
     */
    DisposableEffect(parentId) {

        val registration =
            if (parentId.isNullOrBlank()) {

                null

            } else {

                FirebaseFirestore.getInstance()
                    .collection("child")
                    .whereEqualTo("parentId", parentId)
                    .addSnapshotListener { snapshot, exception ->

                        if (exception != null) {

                            errorMessage =
                                exception.message
                                    ?: "تعذر تحميل متابعة الاختبارات التوعوية"

                            isLoading = false

                            return@addSnapshotListener
                        }

                        children = snapshot?.documents
                            .orEmpty()
                            .map { document ->

                                val childId =
                                    document.getString("childId")
                                        ?: document.id

                                val childName =
                                    document.getString("childName")
                                        ?: "طفل"

                                val progressMap =
                                    document.get("quizProgress")
                                            as? Map<*, *>

                                val progressList = progressMap
                                    ?.mapNotNull { (weekKey, value) ->

                                        val week =
                                            weekKey as? String
                                                ?: return@mapNotNull null

                                        val details =
                                            value as? Map<*, *>
                                                ?: return@mapNotNull null

                                        WeeklyQuizProgress(
                                            week = week,
                                            status = details["status"]
                                                    as? String ?: "pending",
                                            category = details["category"]
                                                    as? String ?: "",
                                            completedAt = (
                                                    details["completedAt"]
                                                            as? Timestamp
                                                    )?.toDate()
                                        )
                                    }
                                    .orEmpty()

                                ChildQuizReport(
                                    childId = childId,
                                    childName = childName,
                                    progress = progressList
                                )
                            }
                            .sortedBy {
                                it.childName
                            }

                        errorMessage = null
                        isLoading = false
                    }
            }

        onDispose {
            registration?.remove()
        }
    }

    // Firestore listener remains active while changing weeks or expanding the card.
    // Only completed quizzes for the selected week are shown when expanded.
    val completedChildren = children.filter { child ->
        child.progress.any { record ->
            record.week == selectedWeekStart && record.status.equals("completed", ignoreCase = true)
        }
    }
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = Color(0xFFE3EDF2),
                                    shape = RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = quizTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "متابعة الاختبارات التوعوية",
                            fontSize = 18.sp,
                            fontFamily = quizFont,
                            fontWeight = FontWeight.Bold,
                            color = quizTeal
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            parentId.isNullOrBlank() -> "يرجى تسجيل الدخول"
                            isLoading -> "جارٍ تحديث نتائج الاختبارات..."
                            errorMessage != null -> "تعذر تحديث نتائج الاختبارات"
                            else -> "الأطفال الذين أكملوا الاختبار: ${completedChildren.size}"
                        },
                        fontSize = 12.sp,
                        fontFamily = quizFont,
                        color = Color.Gray
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "إخفاء أسماء الأطفال" else "عرض أسماء الأطفال",
                    tint = quizTeal
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFE5EBEF))
                    Spacer(modifier = Modifier.height(12.dp))
                    when {
                        parentId.isNullOrBlank() -> Text(
                            text = "يرجى تسجيل الدخول لعرض نتائج الأطفال",
                            fontFamily = quizFont,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        isLoading -> CircularProgressIndicator(
                            color = quizTeal,
                            modifier = Modifier.size(26.dp).align(Alignment.CenterHorizontally)
                        )
                        errorMessage != null -> Text(
                            text = errorMessage ?: "تعذر تحميل النتائج",
                            fontFamily = quizFont,
                            color = Color(0xFFC62828),
                            fontSize = 12.sp
                        )
                        completedChildren.isEmpty() -> Text(
                            text = "لا توجد اختبارات مكتملة لهذا الأسبوع",
                            fontFamily = quizFont,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        else -> completedChildren.forEachIndexed { index, child ->
                            ChildQuizProgressItem(
                                child = child,
                                selectedWeekStart = selectedWeekStart
                            )
                            if (index < completedChildren.lastIndex) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFE5EBEF))
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Displays the current status and previous quizzes
 * for one child.
 */
@Composable
private fun ChildQuizProgressItem(
    child: ChildQuizReport,
    selectedWeekStart: String
) {

    var showHistory by remember(child.childId, selectedWeekStart) {
        mutableStateOf(false)
    }

    val currentProgress = child.progress
        .firstOrNull {
            it.week == selectedWeekStart
        }

    val previousProgress = child.progress
        .filter {
            it.week < selectedWeekStart
        }
        .sortedByDescending {
            it.week
        }
        .take(6)

    val statusText: String
    val statusColor: Color

    when (currentProgress?.status) {

        "completed" -> {

            statusText = "✅ مكتمل"
            statusColor = Color(0xFF2E7D32)
        }

        "pending" -> {

            statusText = "⏳ بانتظار الحل"
            statusColor = Color(0xFFEF6C00)
        }

        else -> {

            statusText = ""
            statusColor = Color.Gray
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = child.childName,
                fontSize = 15.sp,
                fontFamily = quizFont,
                fontWeight = FontWeight.Bold,
                color = quizTeal,
                modifier = Modifier.weight(1f)
            )

            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontFamily = quizFont,
                    color = statusColor
                )
            }
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        if (currentProgress != null) {

            Text(
                text = "نوع الاختبار: ${
                    quizCategoryName(currentProgress.category)
                }",
                fontSize = 12.sp,
                fontFamily = quizFont,
                color = Color.DarkGray
            )

            if (currentProgress.status == "completed") {

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "تاريخ الإكمال: ${
                        formatQuizCompletionDate(
                            currentProgress.completedAt
                        )
                    }",
                    fontSize = 12.sp,
                    fontFamily = quizFont,
                    color = Color.Gray
                )
            }

        }

        if (previousProgress.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            TextButton(
                onClick = {
                    showHistory = !showHistory
                }
            ) {

                Text(
                    text = if (showHistory) {
                        "إخفاء سجل الاختبارات"
                    } else {
                        "عرض سجل الاختبارات السابقة"
                    },
                    fontSize = 12.sp,
                    fontFamily = quizFont,
                    color = quizTeal
                )
            }

            if (showHistory) {

                previousProgress.forEach { record ->

                    val historyStatus =
                        if (record.status == "completed") {
                            "✅ مكتمل"
                        } else {
                            "لم يكتمل"
                        }

                    Text(
                        text = "الأسبوع ${record.week}: $historyStatus",
                        fontSize = 12.sp,
                        fontFamily = quizFont,
                        color = Color.DarkGray
                    )

                    Text(
                        text = quizCategoryName(record.category),
                        fontSize = 11.sp,
                        fontFamily = quizFont,
                        color = Color.Gray
                    )

                    if (record.status == "completed") {

                        Text(
                            text = formatQuizCompletionDate(
                                record.completedAt
                            ),
                            fontSize = 11.sp,
                            fontFamily = quizFont,
                            color = Color.Gray
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Converts AI model labels into child-friendly Arabic.
 */
private fun quizCategoryName(category: String): String {

    return when (
        category.trim().lowercase(Locale.ROOT)
    ) {

        "offensive" -> "الإساءة اللفظية"

        "sexism" -> "التمييز على أساس الجنس"

        "religious discrimination" -> "التمييز الديني"

        "racism" -> "العنصرية العرقية"

        else -> category
    }
}

/**
 * Formats the Firestore completion timestamp.
 */
private fun formatQuizCompletionDate(date: Date?): String {

    if (date == null) {
        return "لم يتوفر التاريخ بعد"
    }

    return SimpleDateFormat(
        "dd/MM/yyyy - HH:mm",
        Locale("ar", "SA")
    ).format(date)
}