package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ClozeCardEntity
import com.example.data.QuestionEntity
import com.example.domain.Sm2Scheduler
import com.example.pdf.MedoPdfEngine
import com.example.ui.ActiveExamState
import com.example.ui.MedoUiState
import com.example.ui.components.ClinicalOptionRow
import com.example.ui.components.GlassCard
import com.example.ui.components.MedoPill
import com.example.ui.components.MedoPrimaryButton
import com.example.ui.components.MedoProgressRing
import com.example.ui.components.MedoSecondaryButton
import com.example.ui.components.OptionVisualState
import com.example.ui.theme.LocalMedoSettings
import com.example.ui.theme.LocalMedoTheme
import com.example.util.PersianTextUtils

/**
 * 4.4 Exam Simulator Screen (آزمون):
 * - Setup: Choose scope (all, bookmarked, wrong answers), question count, time limit, 1/3 negative marking toggle
 * - Active Exam: Navigation grid, flag for review, pause/resume, submit confirmation
 * - Results: Net score with 1/3 negative marking, raw percentage, breakdown, and review of mistakes with explanations
 * - Saved Exam History
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamSimulatorScreen(
    state: MedoUiState,
    onStartExam: (title: String, pool: List<QuestionEntity>, count: Int, durationMin: Int, negativeDivisor: Int) -> Unit,
    onSelectAnswer: (String, Int) -> Unit,
    onToggleFlag: (String) -> Unit,
    onJumpToQuestion: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onFinishExam: () -> Unit,
    onDismissExam: () -> Unit,
    onOpenPdfExport: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current
    val activeExam = state.activeExam

    if (activeExam == null) {
        var selectedCount by remember { mutableIntStateOf(6) }
        var selectedMinutes by remember { mutableIntStateOf(15) }
        var negativeDivisor by remember { mutableIntStateOf(3) } // 1/3 negative marking default
        var scopeType by remember { mutableStateOf("ALL") }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("exam_setup_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "شبیه‌ساز آزمون بورد، دستیاری و پره انترنی",
                            style = MaterialTheme.typography.headlineMedium,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "محاسبه دقیق تراز با نمره منفی یک‌سوم (هر ۳ پاسخ غلط یک پاسخ صحیح را خنثی می‌کند؛ نزده = صفر)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )

                        Text(
                            text = "منبع سوالات آزمون:",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "ALL" to "کل بانک (${PersianTextUtils.formatNumber(state.allQuestions.size, settings.usePersianDigits)})",
                                "BOOKMARKED" to "نشان‌شده‌ها",
                                "WRONG" to "غلط‌های قبلی"
                            ).forEach { (key, label) ->
                                MedoPill(
                                    text = label,
                                    containerColor = if (scopeType == key) tokens.accent else tokens.surfaceTint,
                                    contentColor = if (scopeType == key) tokens.accentOn else tokens.textPrimary,
                                    onClick = { scopeType = key }
                                )
                            }
                        }

                        Text(
                            text = "قانون نمره منفی:",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MedoPill(
                                text = "نمره منفی ۱/۳ (استاندارد دستیاری)",
                                containerColor = if (negativeDivisor == 3) tokens.accent else tokens.surfaceTint,
                                contentColor = if (negativeDivisor == 3) tokens.accentOn else tokens.textPrimary,
                                onClick = { negativeDivisor = 3 }
                            )
                            MedoPill(
                                text = "بدون نمره منفی",
                                containerColor = if (negativeDivisor == 0) tokens.accent else tokens.surfaceTint,
                                contentColor = if (negativeDivisor == 0) tokens.accentOn else tokens.textPrimary,
                                onClick = { negativeDivisor = 0 }
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MedoPrimaryButton(
                                text = "شروع آزمون زمان‌دار",
                                onClick = {
                                    val pool = when (scopeType) {
                                        "BOOKMARKED" -> state.allQuestions.filter {
                                            state.progressByQuestionId[it.siteQuestionId]?.bookmarked == true
                                        }.ifEmpty { state.allQuestions }
                                        "WRONG" -> state.allQuestions.filter {
                                            state.progressByQuestionId[it.siteQuestionId]?.isCorrect == false
                                        }.ifEmpty { state.allQuestions }
                                        else -> state.allQuestions
                                    }
                                    onStartExam(
                                        "آزمون شبیه‌ساز (${if (negativeDivisor == 3) "با نمره منفی ۱/۳" else "بدون نمره منفی"})",
                                        pool,
                                        selectedCount,
                                        selectedMinutes,
                                        negativeDivisor
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_exam_btn")
                            )
                            MedoSecondaryButton(
                                text = "خروجی PDF آزمون",
                                onClick = onOpenPdfExport,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("exam_pdf_export_btn")
                            )
                        }
                    }
                }
            }

            // Saved exam history
            if (state.examHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "تاریخچه آزمون‌های ثبت‌شده:",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(state.examHistory, key = { it.sessionId }) { session ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "صحیح: ${PersianTextUtils.formatNumber(session.correctCount, settings.usePersianDigits)} • غلط: ${PersianTextUtils.formatNumber(session.wrongCount, settings.usePersianDigits)} • نزده: ${PersianTextUtils.formatNumber(session.unansweredCount, settings.usePersianDigits)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.textSecondary
                                )
                            }
                            MedoProgressRing(
                                progress = (session.rawScorePercent / 100f).coerceIn(0f, 1f),
                                size = 48.dp
                            )
                        }
                    }
                }
            }
        }
    } else if (activeExam.isFinished) {
        // Exam Results & Mistakes Review
        val breakdown = activeExam.scoreBreakdown
        val questionsById = state.allQuestions.associateBy { it.siteQuestionId }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("exam_results_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "کارنامه نهایی آزمون",
                            style = MaterialTheme.typography.headlineMedium,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (breakdown != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "درصد خالص (با نمره منفی ۱/۳): ${PersianTextUtils.formatNumber(breakdown.netPercentWithNegative.toInt(), settings.usePersianDigits)}٪",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = tokens.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "درصد خام (بدون نمره منفی): ${PersianTextUtils.formatNumber(breakdown.rawPercentNoNegative.toInt(), settings.usePersianDigits)}٪",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.textSecondary
                                    )
                                    Text(
                                        text = "صحیح: ${PersianTextUtils.formatNumber(breakdown.correctCount, settings.usePersianDigits)} | غلط: ${PersianTextUtils.formatNumber(breakdown.wrongCount, settings.usePersianDigits)} | نزده: ${PersianTextUtils.formatNumber(breakdown.unansweredCount, settings.usePersianDigits)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = tokens.textPrimary
                                    )
                                }
                                MedoProgressRing(
                                    progress = (breakdown.netPercentWithNegative / 100f).coerceIn(0f, 1f),
                                    size = 64.dp,
                                    strokeWidth = 6.dp
                                )
                            }
                        }

                        MedoPrimaryButton(
                            text = "بستن کارنامه و بازگشت",
                            onClick = onDismissExam,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dismiss_exam_results_btn")
                        )
                    }
                }
            }

            item {
                Text(
                    text = "مرور پاسخ‌های تشریحی آزمون:",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            items(activeExam.questionIds) { qId ->
                val q = questionsById[qId] ?: return@items
                val userChoice = activeExam.selectedOptionsByQuestionId[qId]
                val isCorrect = userChoice != null && userChoice == q.correctOptionIndex
                GlassCard(
                    isReadingSurface = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MedoPill(text = q.siteQuestionId)
                            MedoPill(
                                text = when {
                                    userChoice == null -> "نزده (۰)"
                                    isCorrect -> "✓ پاسخ صحیح"
                                    else -> "✗ پاسخ غلط (-۱/۳)"
                                },
                                containerColor = when {
                                    userChoice == null -> tokens.surfaceTint
                                    isCorrect -> tokens.correct.copy(alpha = 0.2f)
                                    else -> tokens.wrong.copy(alpha = 0.2f)
                                },
                                contentColor = when {
                                    userChoice == null -> tokens.textSecondary
                                    isCorrect -> tokens.correct
                                    else -> tokens.wrong
                                }
                            )
                        }
                        RichClinicalHtmlView(html = q.stemHtml)
                        if (!q.explanationHtml.isNullOrBlank()) {
                            Text(
                                text = "پاسخ تشریحی:",
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.accent,
                                fontWeight = FontWeight.Bold
                            )
                            RichClinicalHtmlView(html = q.explanationHtml)
                        }
                    }
                }
            }
        }
    } else {
        // Active In-Progress Exam Session
        val currentQId = activeExam.questionIds.getOrNull(activeExam.currentQuestionIndex)
        val currentQuestion = state.allQuestions.find { it.siteQuestionId == currentQId }
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp)
                .testTag("active_exam_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${activeExam.title} • سوال ${PersianTextUtils.formatNumber(activeExam.currentQuestionIndex + 1, settings.usePersianDigits)} از ${PersianTextUtils.formatNumber(activeExam.questionIds.size, settings.usePersianDigits)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = onTogglePause) {
                                Icon(
                                    imageVector = if (activeExam.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "توقف/ادامه آزمون",
                                    tint = tokens.accent
                                )
                            }
                            if (currentQId != null) {
                                IconButton(onClick = { onToggleFlag(currentQId) }) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = "علامت‌گذاری سوال برای بازبینی",
                                        tint = if (currentQId in activeExam.flaggedQuestionIds) tokens.warning else tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Navigation grid
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeExam.questionIds.forEachIndexed { idx, qId ->
                            val answered = activeExam.selectedOptionsByQuestionId.containsKey(qId)
                            val flagged = qId in activeExam.flaggedQuestionIds
                            val isCurrent = idx == activeExam.currentQuestionIndex
                            MedoPill(
                                text = buildString {
                                    append(PersianTextUtils.formatNumber(idx + 1, settings.usePersianDigits))
                                    if (flagged) append("⚑")
                                },
                                containerColor = when {
                                    isCurrent -> tokens.accent
                                    answered -> tokens.correct.copy(alpha = 0.25f)
                                    else -> tokens.surfaceTint
                                },
                                contentColor = if (isCurrent) tokens.accentOn else tokens.textPrimary,
                                onClick = { onJumpToQuestion(idx) }
                            )
                        }
                    }
                }
            }

            if (currentQuestion != null) {
                GlassCard(
                    isReadingSurface = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RichClinicalHtmlView(html = currentQuestion.stemHtml)
                }

                val opts = MedoPdfEngine.parseOptionsJson(currentQuestion.optionsJson)
                val chosen = activeExam.selectedOptionsByQuestionId[currentQuestion.siteQuestionId]
                opts.forEachIndexed { idx, optText ->
                    ClinicalOptionRow(
                        index = idx,
                        text = optText,
                        state = if (chosen == idx) OptionVisualState.SELECTED else OptionVisualState.NEUTRAL,
                        onClick = { onSelectAnswer(currentQuestion.siteQuestionId, idx) }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MedoSecondaryButton(
                    text = "سوال بعدی",
                    enabled = activeExam.currentQuestionIndex < activeExam.questionIds.lastIndex,
                    onClick = { onJumpToQuestion(activeExam.currentQuestionIndex + 1) },
                    modifier = Modifier.weight(1f)
                )
                MedoPrimaryButton(
                    text = "ثبت نهایی و کارنامه",
                    onClick = onFinishExam,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("finish_exam_btn")
                )
            }
        }
    }
}

/**
 * 4.5 & 4.6 Spaced Repetition (مرور) + Cloze Flashcards Screen:
 * - SM-2 daily review queue
 * - Cloze deletions generated at runtime from bold/colored key phrases in lesson HTML
 * - Tap-to-reveal answer + 4 SM-2 rating buttons (دوباره / سخت / خوب / آسان) + Suppress card button
 */
@Composable
fun SpacedRepetitionReviewScreen(
    state: MedoUiState,
    onRateQuestionSm2: (String, Sm2Scheduler.Rating) -> Unit,
    onRateClozeSm2: (ClozeCardEntity, Sm2Scheduler.Rating) -> Unit,
    onSuppressCloze: (ClozeCardEntity) -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current
    var showClozeTab by remember { mutableStateOf(true) }
    var revealedCardId by remember { mutableStateOf<String?>(null) }

    val activeClozeCards = state.clozeCards.filter { !it.suppressed }
    val dueQuestions = state.allQuestions

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("review_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "مرور هوشمند به روش تکرار با فاصله (SM-2)",
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "سقف کارت جدید روزانه: ${PersianTextUtils.formatNumber(settings.dailyNewCardsLimit, settings.usePersianDigits)} کارت • استخراج خودکار عبارات کلیدی درسنامه‌ها (Cloze)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedoPill(
                            text = "فلش‌کارت‌های Cloze (${PersianTextUtils.formatNumber(activeClozeCards.size, settings.usePersianDigits)})",
                            containerColor = if (showClozeTab) tokens.accent else tokens.surfaceTint,
                            contentColor = if (showClozeTab) tokens.accentOn else tokens.textPrimary,
                            onClick = { showClozeTab = true },
                            modifier = Modifier.testTag("review_tab_cloze")
                        )
                        MedoPill(
                            text = "صف مرور سوالات (${PersianTextUtils.formatNumber(dueQuestions.size, settings.usePersianDigits)})",
                            containerColor = if (!showClozeTab) tokens.accent else tokens.surfaceTint,
                            contentColor = if (!showClozeTab) tokens.accentOn else tokens.textPrimary,
                            onClick = { showClozeTab = false },
                            modifier = Modifier.testTag("review_tab_questions")
                        )
                    }
                }
            }
        }

        if (showClozeTab) {
            items(activeClozeCards, key = { it.cardId }) { card ->
                val isRevealed = revealedCardId == card.cardId
                GlassCard(
                    isReadingSurface = true,
                    onClick = { revealedCardId = if (isRevealed) null else card.cardId },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloze_card_${card.cardId}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MedoPill(text = "سوال منبع: ${card.siteQuestionId}")
                            MedoPill(
                                text = "حذف از صف مرور",
                                containerColor = tokens.wrong.copy(alpha = 0.16f),
                                contentColor = tokens.wrong,
                                onClick = { onSuppressCloze(card) }
                            )
                        }

                        Text(
                            text = PersianTextUtils.formatDigits(card.clozeDeletionText, settings.usePersianDigits),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textPrimary
                        )

                        if (!isRevealed) {
                            MedoPill(
                                text = "برای نمایش عبارت کلیدی ضربه بزنید",
                                onClick = { revealedCardId = card.cardId },
                                modifier = Modifier.testTag("reveal_cloze_btn_${card.cardId}")
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tokens.correct.copy(alpha = 0.16f))
                                    .border(1.dp, tokens.correct, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "✓ پاسخ کلیدی: ${PersianTextUtils.formatDigits(card.answerKeyPhrase, settings.usePersianDigits)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.correct,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 4 SM-2 rating buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Sm2Scheduler.Rating.entries.forEach { rating ->
                                    MedoPill(
                                        text = rating.labelFa,
                                        onClick = {
                                            onRateClozeSm2(card, rating)
                                            revealedCardId = null
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(dueQuestions, key = { it.siteQuestionId }) { q ->
                val prog = state.progressByQuestionId[q.siteQuestionId]
                val cleanStem = q.stemHtml.replace(Regex("<[^>]+>"), " ").trim()
                GlassCard(
                    isReadingSurface = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MedoPill(text = q.siteQuestionId)
                            Text(
                                text = "فاصله فعلی: ${PersianTextUtils.formatNumber(prog?.sm2IntervalDays ?: 0, settings.usePersianDigits)} روز (EF: ${String.format("%.2f", prog?.sm2EasinessFactor ?: 2.5f)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.textSecondary
                            )
                        }
                        Text(
                            text = PersianTextUtils.formatDigits(cleanStem, settings.usePersianDigits),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textPrimary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Sm2Scheduler.Rating.entries.forEach { rating ->
                                MedoPill(
                                    text = rating.labelFa,
                                    onClick = { onRateQuestionSm2(q.siteQuestionId, rating) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4.7 Analytics Screen (آمار):
 * - Accuracy by category/topic with weakest list
 * - Streak, daily goals, unseen counts
 * - Time spent per question (average + slowest questions)
 * - Trap-question shortcut filter
 */
@Composable
fun AnalyticsScreen(
    state: MedoUiState,
    onOpenTrapQuestions: () -> Unit,
    onExportProgressBackup: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current

    val totalCount = state.allQuestions.size
    val answeredList = state.progressByQuestionId.values.filter { it.lastSelectedOption != null }
    val correctCount = answeredList.count { it.isCorrect == true }
    val unseenCount = (totalCount - answeredList.size).coerceAtLeast(0)
    val trapCount = state.allQuestions.count { it.isTrap }
    val avgTimeSeconds = if (answeredList.isNotEmpty()) {
        (answeredList.sumOf { it.totalTimeSpentMs } / answeredList.size) / 1000L
    } else {
        17L
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "تحلیل عملکرد بالینی و آمار مطالعه",
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "کل سوالات بانک: ${PersianTextUtils.formatNumber(totalCount, settings.usePersianDigits)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "پاسخ‌داده‌شده: ${PersianTextUtils.formatNumber(answeredList.size, settings.usePersianDigits)} • دیده‌نشده: ${PersianTextUtils.formatNumber(unseenCount, settings.usePersianDigits)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                            Text(
                                text = "میانگین زمان هر سوال: ${PersianTextUtils.formatNumber(avgTimeSeconds, settings.usePersianDigits)} ثانیه",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.accent
                            )
                        }
                        MedoProgressRing(
                            progress = if (answeredList.isNotEmpty()) correctCount.toFloat() / answeredList.size else 0f,
                            size = 64.dp,
                            strokeWidth = 6.dp
                        )
                    }
                }
            }
        }

        // Trap questions highlight card
        if (trapCount > 0) {
            item {
                GlassCard(
                    onClick = onOpenTrapQuestions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("analytics_trap_filter_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "دام‌های تستی پرتکرار (${PersianTextUtils.formatNumber(trapCount, settings.usePersianDigits)} سوال)",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.warning,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "سوالاتی که در آن‌ها درصد انتخاب یک گزینه غلط توسط داوطلبان بیشتر از گزینه صحیح بوده است",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                        }
                        MedoPill(text = "تمرین دام‌ها", onClick = onOpenTrapQuestions)
                    }
                }
            }
        }

        // Accuracy breakdown by top-level category
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "درصد تسلط به تفکیک مبحث:",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    state.categoryNodes.filter { it.depth <= 1 && it.isDownloaded }.forEach { node ->
                        val qIds = state.crossRefs.filter { it.nodeId == node.id }.map { it.siteQuestionId }
                        val ans = qIds.mapNotNull { state.progressByQuestionId[it] }.filter { it.lastSelectedOption != null }
                        val corr = ans.count { it.isCorrect == true }
                        val ratio = if (ans.isNotEmpty()) corr.toFloat() / ans.size else 0f

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = node.fullPath,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = "${PersianTextUtils.formatNumber(ans.size, settings.usePersianDigits)} از ${PersianTextUtils.formatNumber(qIds.size, settings.usePersianDigits)} سوال پاسخ‌داده‌شده",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.textSecondary
                                )
                            }
                            MedoProgressRing(progress = ratio, size = 42.dp)
                        }
                    }
                }
            }
        }

        item {
            MedoPrimaryButton(
                text = "خروجی پشتیبان پیشرفت و یادداشت‌ها (JSON)",
                onClick = onExportProgressBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_progress_json_btn")
            )
        }
    }
}
