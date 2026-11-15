package com.example.Hami.quiz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.Hami.R
import com.example.Hami.quiz.model.Quiz
import com.example.Hami.quiz.model.QuizDifficulty
import kotlinx.coroutines.delay

// Same font + palette as the rest of Hami (see ChildStatusActivity / DashboardActivity)
private val AlfontDark = FontFamily(Font(R.font.alfont_com_dark, FontWeight.Normal))
private val HamiTeal = Color(0xFF52879C)
private val HamiGray = Color(0xFF788B94)
private val HamiLight = Color(0xFFC0D4DF)
private val CorrectGreen = Color(0xFF4CAF50)
private val WrongRed = Color(0xFFD32F2F)
private val HamiTealDark = Color(0xFF3D6A7D) // darker shade of HamiTeal for better contrast

// Soft rounded shapes (replace the previous square corners)
private val CardShape = RoundedCornerShape(28.dp)
private val InnerShape = RoundedCornerShape(16.dp)

private enum class OptionState { Idle, Selected, Correct, Wrong }

@Composable
fun QuizOverlayContent(quiz: Quiz, onFinish: () -> Unit, onQuestionShown: (String) -> Unit = {}) {
    // All child-facing text is Arabic, so the whole overlay is forced RTL
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val speech = rememberQuizSpeech() // read-aloud only; independent of quiz state
        var questionIndex by remember { mutableIntStateOf(0) }
        var finished by remember { mutableStateOf(false) }

        // "Medium" card: at most 90% of the width (max 420dp) and 75% of the height
        val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000)), // dimmed scrim; blocks the app underneath
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 420.dp)
                    .heightIn(max = maxCardHeight)
                    .shadow(12.dp, CardShape)
                    .background(
                        Brush.linearGradient(colors = listOf(Color.White, HamiLight)),
                        CardShape
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!finished) {
                    // key() gives every question fresh state (selection, feedback, timer)
                    key(questionIndex) {
                        // Reported once per question, at the moment it appears (feeds the no-repeat history)
                        LaunchedEffect(questionIndex) { onQuestionShown(quiz.questions[questionIndex].id) }
                        QuestionStep(
                            quiz = quiz,
                            index = questionIndex,
                            speech = speech,
                            onNext = {
                                if (questionIndex < quiz.questions.lastIndex) questionIndex++
                                else finished = true
                            }
                        )
                    }
                } else {
                    CompletionStep(quiz = quiz, onFinish = onFinish)
                }
            }
        }
    }
}

@Composable
private fun QuestionStep(quiz: Quiz, index: Int, speech: QuizSpeech, onNext: () -> Unit) {
    val q = quiz.questions[index]

    // Leaving this question (next / finish) stops any reading so a previous question is never read aloud
    DisposableEffect(q.id) { onDispose { speech.stop() } }
    val total = quiz.questions.size

    // Shuffled display order (values are ORIGINAL option indices) so the right answer isn't always in the same spot
    val order = remember(q.id) { q.options.indices.shuffled() }
    var selected by remember(q.id) { mutableStateOf<Int?>(null) }
    var checked by remember(q.id) { mutableStateOf(false) }
    var remaining by remember(q.id) { mutableIntStateOf(q.timerSeconds.coerceAtLeast(1)) }

    // Timer: counts down once per question. At 0 it only shows a gentle message - it never skips ahead.
    LaunchedEffect(q.id, checked) {
        if (!checked) {
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1
            }
        }
    }

    val isCorrect = checked && selected == q.correctAnswerIndex
    val timeUp = remaining == 0 && !checked

    // Header
    Text("حامي 🛡️", fontSize = 26.sp, color = HamiTeal, fontFamily = AlfontDark)
    Spacer(Modifier.height(6.dp))
    Text(
        "السؤال ${index + 1} من $total",
        fontSize = 14.sp, color = HamiGray, fontFamily = AlfontDark
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(1.5.dp, HamiTeal, CircleShape)
                    .background(if (i <= index) HamiTeal else Color.Transparent, CircleShape)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    val urgent = remaining <= 3 && !checked
    Box(
        modifier = Modifier
            .background(if (urgent) Color(0x1FD32F2F) else Color(0x1F52879C), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            "الوقت: ${"%02d".format(remaining)}",
            fontSize = 16.sp,
            color = if (urgent) WrongRed else HamiTealDark,
            fontFamily = AlfontDark
        )
    }
    Spacer(Modifier.height(12.dp))

    // Question
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, InnerShape)
            .border(2.dp, HamiLight, InnerShape)
            .padding(16.dp)
    ) {
        Text(
            q.question,
            fontSize = 22.sp,
            lineHeight = 32.sp,
            color = Color(0xFF2B3A42),
            fontFamily = AlfontDark,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        // Reads ONLY the current question's text, and ONLY when the child taps
        ReadQuestionButton(
            status = speech.status,
            onRead = {
                val textToRead = if (quiz.difficulty == QuizDifficulty.EASY) {
                    q.question + ". " + order.joinToString(". ") { q.options[it] }
                } else q.question
                speech.speak(textToRead)
            },
            onStop = { speech.stop() },
            includeOptions = quiz.difficulty == QuizDifficulty.EASY
        )
    }
    Spacer(Modifier.height(14.dp))

    if (timeUp) {
        Text(
            "انتهى الوقت ⏱️",
            fontSize = 16.sp, color = HamiTeal, fontFamily = AlfontDark,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "خذ لحظة لقراءة السؤال مرة أخرى واختر الإجابة التي تراها مناسبة.",
            fontSize = 14.sp, color = HamiGray, fontFamily = AlfontDark,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }

    // Options
    order.forEach { optionIndex ->
        val state = when {
            checked && optionIndex == q.correctAnswerIndex -> OptionState.Correct
            checked && optionIndex == selected -> OptionState.Wrong
            !checked && optionIndex == selected -> OptionState.Selected
            else -> OptionState.Idle
        }
        OptionRow(
            text = q.options[optionIndex],
            state = state,
            enabled = !checked,
            onClick = { selected = optionIndex }
        )
        Spacer(Modifier.height(8.dp))
    }

    // Feedback (educational, not just "wrong")
    if (checked) {
        Spacer(Modifier.height(4.dp))
        FeedbackBox(
            isCorrect = isCorrect,
            explanation = q.explanation,
            takeaway = quiz.takeaway
        )
    }

    Spacer(Modifier.height(14.dp))

    // Buttons: "تحقق" needs a selection; "التالي" stays disabled until the answer was checked.
    // There is intentionally no skip / close / back control.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HamiButton(
            text = "تحقق",
            enabled = selected != null && !checked,
            modifier = Modifier.weight(1f),
            onClick = { if (selected != null) checked = true }
        )
        HamiButton(
            text = "التالي",
            enabled = checked,
            modifier = Modifier.weight(1f),
            onClick = onNext
        )
    }
}

@Composable
private fun ReadQuestionButton(status: SpeechStatus, onRead: () -> Unit, onStop: () -> Unit, includeOptions: Boolean = false) {
    val speaking = status == SpeechStatus.Speaking
    Button(
        onClick = if (speaking) onStop else onRead,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = if (speaking) "إيقاف القراءة" else if (includeOptions) "اقرأ السؤال والخيارات" else "اقرأ السؤال" },
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (speaking) HamiTealDark else HamiTeal,
            contentColor = Color.White
        )
    ) {
        Text(
            if (speaking) "⏹ إيقاف" else if (includeOptions) "🔊 اقرأ السؤال والخيارات" else "🔊 اقرأ السؤال",
            fontSize = 16.sp, fontFamily = AlfontDark, color = Color.White
        )
    }
    // Gentle hint only; answering is never blocked by speech problems
    val hint = when (status) {
        SpeechStatus.NotReady -> "الصوت يحتاج لحظة، حاول مرة أخرى."
        SpeechStatus.Unavailable -> "القراءة الصوتية غير متاحة على هذا الجهاز."
        else -> null
    }
    if (hint != null) {
        Spacer(Modifier.height(6.dp))
        Text(hint, fontSize = 13.sp, color = HamiTealDark, fontFamily = AlfontDark)
    }
}

@Composable
private fun OptionRow(text: String, state: OptionState, enabled: Boolean, onClick: () -> Unit) {
    val borderTarget = when (state) {
        OptionState.Idle -> HamiLight
        OptionState.Selected -> HamiTeal
        OptionState.Correct -> CorrectGreen
        OptionState.Wrong -> WrongRed
    }
    val fillTarget = when (state) {
        OptionState.Idle -> Color.White
        OptionState.Selected -> Color(0x3352879C)
        OptionState.Correct -> Color(0x334CAF50)
        OptionState.Wrong -> Color(0x33D32F2F)
    }
    // Short colour fade only; purely visual
    val borderColor by animateColorAsState(borderTarget, tween(150), label = "optionBorder")
    val fill by animateColorAsState(fillTarget, tween(150), label = "optionFill")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(InnerShape)
            .background(fill, InnerShape)
            .border(2.dp, borderColor, InnerShape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .border(2.dp, borderColor, CircleShape)
                .background(
                    if (state == OptionState.Idle) Color.Transparent else borderColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // A mark (not just colour) for right/wrong, so it is readable without relying on colour
            val mark = when (state) {
                OptionState.Correct -> "✓"
                OptionState.Wrong -> "✕"
                else -> ""
            }
            if (mark.isNotEmpty()) Text(mark, fontSize = 14.sp, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            fontSize = 17.sp,
            color = Color(0xFF2B3A42),
            fontFamily = AlfontDark,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FeedbackBox(isCorrect: Boolean, explanation: String, takeaway: String) {
    val accent = if (isCorrect) CorrectGreen else WrongRed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, InnerShape)
            .border(2.dp, accent, InnerShape)
            .padding(12.dp)
    ) {
        Text(
            if (isCorrect) "✓ إجابة صحيحة!" else "❌ إجابة غير صحيحة",
            fontSize = 17.sp, color = accent, fontFamily = AlfontDark
        )
        Spacer(Modifier.height(6.dp))
        if (isCorrect) {
            Text("أحسنت.", fontSize = 14.sp, color = HamiTeal, fontFamily = AlfontDark)
        } else {
            Text("لماذا؟", fontSize = 14.sp, color = HamiTeal, fontFamily = AlfontDark)
        }
        Text(
            explanation,
            fontSize = 14.sp, color = Color(0xFF2B3A42), fontFamily = AlfontDark,
            textAlign = TextAlign.Start
        )
        if (!isCorrect && takeaway.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("تذكر:", fontSize = 14.sp, color = HamiTeal, fontFamily = AlfontDark)
            Text(
                takeaway,
                fontSize = 14.sp, color = Color(0xFF2B3A42), fontFamily = AlfontDark,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun CompletionStep(quiz: Quiz, onFinish: () -> Unit) {
    var tapped by remember { mutableStateOf(false) }

    Text("حامي 🛡️", fontSize = 26.sp, color = HamiTeal, fontFamily = AlfontDark)
    Spacer(Modifier.height(12.dp))
    Text("🎉 أحسنت!", fontSize = 24.sp, color = CorrectGreen, fontFamily = AlfontDark)
    Spacer(Modifier.height(8.dp))
    Text("أكملت النشاط التعليمي.", fontSize = 16.sp, color = Color(0xFF2B3A42), fontFamily = AlfontDark)
    Spacer(Modifier.height(12.dp))
    Text(
        "تذكر دائمًا:",
        fontSize = 15.sp, color = HamiTeal, fontFamily = AlfontDark,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "الكلمات التي نستخدمها قد تؤثر في الآخرين، والاحترام يجعل التواصل أكثر أمانًا.",
        fontSize = 15.sp, color = Color(0xFF2B3A42), fontFamily = AlfontDark,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
    if (quiz.takeaway.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            quiz.takeaway,
            fontSize = 14.sp, color = HamiGray, fontFamily = AlfontDark,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(18.dp))
    HamiButton(
        text = "إنهاء",
        enabled = !tapped,
        modifier = Modifier.fillMaxWidth(),
        onClick = { tapped = true; onFinish() }
    )
}

@Composable
private fun HamiButton(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = InnerShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = HamiTeal,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFB4C7D0),
            disabledContentColor = Color.White
        )
    ) {
        Text(text, fontSize = 18.sp, fontFamily = AlfontDark, color = Color.White)
    }
}
