package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.QuestionEntity
import com.example.ui.MainNavTab
import com.example.ui.MedoUiState
import com.example.ui.MedoViewModel
import com.example.ui.OverlayModal
import com.example.ui.QuestionFilter
import com.example.ui.QuestionSortOrder
import com.example.ui.components.MedoPill
import com.example.ui.components.MedoProceduralBackground
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DiagnosticsModalScreen
import com.example.ui.screens.ExamSimulatorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LessonReaderDialogScreen
import com.example.ui.screens.MindMapViewerModal
import com.example.ui.screens.NodeQuestionListModal
import com.example.ui.screens.PackManagerModalScreen
import com.example.ui.screens.PdfExportModalScreen
import com.example.ui.screens.PracticeScreen
import com.example.ui.screens.QrScannerModalScreen
import com.example.ui.screens.SettingsModalScreen
import com.example.ui.screens.SpacedRepetitionReviewScreen
import com.example.ui.screens.releaseCameraProviderSafely
import com.example.ui.theme.LocalMedoTheme
import com.example.ui.theme.MedoNextTheme
import com.example.util.PersianTextUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val medoViewModel: MedoViewModel = viewModel()
            val state by medoViewModel.uiState.collectAsStateWithLifecycle()

            MedoNextTheme(settings = state.settings) {
                MedoAppRoot(state = state, viewModel = medoViewModel)
            }
        }
    }
}

@Composable
fun MedoAppRoot(
    state: MedoUiState,
    viewModel: MedoViewModel
) {
    val tokens = LocalMedoTheme.current
    val context = LocalContext.current

    BackHandler(enabled = state.activeModal != OverlayModal.NONE || state.currentTab != MainNavTab.HOME) {
        if (state.activeModal == OverlayModal.QR_SCANNER) {
            releaseCameraProviderSafely(context)
        }
        if (state.activeModal != OverlayModal.NONE) {
            viewModel.closeModal()
        } else {
            viewModel.selectTab(MainNavTab.HOME)
        }
    }

    val filteredQuestions = remember(
        state.allQuestions,
        state.crossRefs,
        state.selectedNodeId,
        state.activeQuestionFilter,
        state.activeSortOrder,
        state.nodeSearchQuery,
        state.progressByQuestionId
    ) {
        filterAndSortQuestions(state)
    }

    val activePracticeQuestion = remember(state.allQuestions, state.currentPracticeQuestionId, filteredQuestions) {
        state.allQuestions.find { it.siteQuestionId == state.currentPracticeQuestionId }
            ?: filteredQuestions.firstOrNull()
            ?: state.allQuestions.firstOrNull()
    }

    val modalSafeModifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .windowInsetsPadding(WindowInsets.safeDrawing)

    MedoProceduralBackground {
        when (state.activeModal) {
            OverlayModal.SETTINGS -> {
                Box(modifier = modalSafeModifier) {
                    SettingsModalScreen(
                        state = state,
                        onUpdateSettings = viewModel::updateSettings,
                        onOpenDiagnostics = { viewModel.openModal(OverlayModal.DIAGNOSTICS) },
                        onExportProgressJson = { viewModel.exportAndShareProgressJson(context) },
                        onClose = viewModel::closeModal
                    )
                }
            }
            OverlayModal.PACK_MANAGER -> {
                Box(modifier = modalSafeModifier) {
                    PackManagerModalScreen(
                        state = state,
                        onOpenQrScanner = { viewModel.openModal(OverlayModal.QR_SCANNER) },
                        onDownloadFromLanServer = { url -> viewModel.downloadAndImportFromLanServer(context, url) },
                        onImportLocalBankUri = { uri -> viewModel.importMedoBankFromUri(context, uri) },
                        onTriggerDeltaUpdate = viewModel::applySimulatedV3DeltaUpdate,
                        onDeletePack = viewModel::deleteInstalledPack,
                        onClose = viewModel::closeModal
                    )
                }
            }
            OverlayModal.QR_SCANNER -> {
                Box(modifier = modalSafeModifier) {
                    QrScannerModalScreen(
                        onConnectValidatedUrl = viewModel::onQrLanUrlValidatedAndConnected,
                        onClose = {
                            releaseCameraProviderSafely(context)
                            viewModel.closeModal()
                        }
                    )
                }
            }
            OverlayModal.DIAGNOSTICS -> {
                Box(modifier = modalSafeModifier) {
                    DiagnosticsModalScreen(
                        state = state,
                        onClose = viewModel::closeModal
                    )
                }
            }
            OverlayModal.PDF_EXPORT -> {
                Box(modifier = modalSafeModifier) {
                    PdfExportModalScreen(
                        state = state,
                        onExportPdf = { config -> viewModel.generateAndSharePdf(context, config) },
                        onClose = viewModel::closeModal
                    )
                }
            }
            OverlayModal.NODE_QUESTION_LIST -> {
                Box(modifier = modalSafeModifier) {
                    NodeQuestionListModal(
                        state = state,
                        filteredQuestions = filteredQuestions,
                        onSelectFilter = viewModel::setQuestionFilter,
                        onSelectSort = viewModel::setSortOrder,
                        onNodeSearchChange = viewModel::setNodeSearchQuery,
                        onOpenQuestion = viewModel::selectQuestionForPractice,
                        onClose = viewModel::closeModal
                    )
                }
            }
            OverlayModal.LESSON_READER -> {
                val q = activePracticeQuestion
                val prog = q?.let { state.progressByQuestionId[it.siteQuestionId] }
                val highlights = com.example.domain.PackDeltaPlanner.parseJsonStringArray(prog?.highlightsJson)
                val resolvedLessonHtml = state.currentPracticeLessonHtml
                    ?: q?.lessonHtml
                    ?: q?.explanationHtml
                    ?: "<p>درسنامه‌ای ثبت نشده است.</p>"
                Box(modifier = modalSafeModifier) {
                    LessonReaderDialogScreen(
                        questionTitle = q?.siteQuestionId ?: "",
                        lessonHtml = resolvedLessonHtml,
                        savedHighlights = highlights,
                        onAddHighlight = { phrase ->
                            if (q != null) viewModel.addHighlightToQuestion(q.siteQuestionId, phrase)
                        },
                        onOpenMindMap = {
                            if (q != null) viewModel.openMindMapForQuestion(q)
                        },
                        hasMindMap = q?.hasMindMap == true,
                        onClose = viewModel::closeModal
                    )
                }
            }
            OverlayModal.MIND_MAP_VIEWER -> {
                Box(modifier = modalSafeModifier) {
                    MindMapViewerModal(
                        mediaHash = state.activeMindMapHash,
                        titleFa = state.activeMindMapTitle,
                        onShareMindMap = {
                            viewModel.openModal(OverlayModal.PDF_EXPORT)
                        },
                        onClose = viewModel::closeModal
                    )
                }
            }
            OverlayModal.NONE -> {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        MedoGlassTopBar(
                            currentTab = state.currentTab,
                            onOpenQrScanner = { viewModel.openModal(OverlayModal.QR_SCANNER) },
                            onOpenPackManager = { viewModel.openModal(OverlayModal.PACK_MANAGER) },
                            onOpenPdfExport = { viewModel.openModal(OverlayModal.PDF_EXPORT) },
                            onOpenDiagnostics = { viewModel.openModal(OverlayModal.DIAGNOSTICS) },
                            onOpenSettings = { viewModel.openModal(OverlayModal.SETTINGS) }
                        )
                    },
                    bottomBar = {
                        MedoGlassBottomBar(
                            currentTab = state.currentTab,
                            onSelectTab = viewModel::selectTab
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        state.statusBannerMessage?.let { msg ->
                            Surface(
                                color = tokens.accent.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.clearStatusBanner() }
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = tokens.textPrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (state.currentTab) {
                                MainNavTab.HOME -> HomeScreen(
                                    state = state,
                                    onContinuePractice = { viewModel.selectTab(MainNavTab.PRACTICE) },
                                    onGoToReview = { viewModel.selectTab(MainNavTab.REVIEW) },
                                    onOpenPackManager = { viewModel.openModal(OverlayModal.PACK_MANAGER) },
                                    onSearchQueryChange = viewModel::updateGlobalSearch,
                                    onSelectSearchResult = viewModel::selectQuestionForPractice,
                                    onSwitchTreeTab = viewModel::setOnlyDownloadedTreeTab,
                                    onToggleNodeExpand = viewModel::toggleNodeExpansion,
                                    onExpandAll = viewModel::expandAllNodes,
                                    onCollapseAll = viewModel::collapseAllNodes,
                                    onOpenNodeQuestionList = viewModel::openNodeQuestionList
                                )
                                MainNavTab.PRACTICE -> {
                                    if (activePracticeQuestion != null) {
                                        PracticeScreen(
                                            state = state,
                                            questionsList = filteredQuestions.ifEmpty { state.allQuestions },
                                            currentQuestion = activePracticeQuestion,
                                            currentLessonHtml = state.currentPracticeLessonHtml ?: activePracticeQuestion.lessonHtml,
                                            currentProgress = state.progressByQuestionId[activePracticeQuestion.siteQuestionId],
                                            onSelectOption = { optIdx ->
                                                viewModel.submitPracticeOption(activePracticeQuestion, optIdx)
                                            },
                                            onToggleImmediateMode = viewModel::toggleImmediateFeedbackMode,
                                            onRevealAnswer = {
                                                viewModel.revealAnswerForCurrentQuestion(activePracticeQuestion.siteQuestionId)
                                            },
                                            onNavigateQuestion = viewModel::selectQuestionForPractice,
                                            onToggleBookmark = { viewModel.toggleBookmark(activePracticeQuestion.siteQuestionId) },
                                            onToggleStar = { viewModel.toggleStar(activePracticeQuestion.siteQuestionId) },
                                            onToggleFlag = { viewModel.toggleFlag(activePracticeQuestion.siteQuestionId) },
                                            onSaveNote = { note ->
                                                viewModel.savePersonalNote(activePracticeQuestion.siteQuestionId, note)
                                            },
                                            onOpenLessonReader = { viewModel.openModal(OverlayModal.LESSON_READER) },
                                            onOpenMindMap = { viewModel.openMindMapForQuestion(activePracticeQuestion) },
                                            onOpenFilterList = { viewModel.openModal(OverlayModal.NODE_QUESTION_LIST) }
                                        )
                                    }
                                }
                                MainNavTab.EXAM -> ExamSimulatorScreen(
                                    state = state,
                                    onStartExam = viewModel::startNewExam,
                                    onSelectAnswer = viewModel::selectExamAnswer,
                                    onToggleFlag = viewModel::toggleExamQuestionFlag,
                                    onJumpToQuestion = viewModel::jumpToExamQuestion,
                                    onTogglePause = viewModel::toggleExamPause,
                                    onFinishExam = viewModel::finishActiveExam,
                                    onDismissExam = viewModel::dismissActiveExam,
                                    onOpenPdfExport = { viewModel.openModal(OverlayModal.PDF_EXPORT) }
                                )
                                MainNavTab.REVIEW -> SpacedRepetitionReviewScreen(
                                    state = state,
                                    onRateQuestionSm2 = viewModel::rateQuestionSm2,
                                    onRateClozeSm2 = viewModel::rateClozeCardSm2,
                                    onSuppressCloze = viewModel::suppressClozeCard
                                )
                                MainNavTab.ANALYTICS -> AnalyticsScreen(
                                    state = state,
                                    onOpenTrapQuestions = {
                                        viewModel.setQuestionFilter(QuestionFilter.TRAP)
                                        viewModel.openModal(OverlayModal.NODE_QUESTION_LIST)
                                    },
                                    onExportProgressBackup = {
                                        viewModel.exportAndShareProgressJson(context)
                                    }
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
 * Top bar with explicit `statusBarsPadding()` and `windowInsetsPadding(WindowInsets.statusBars)`
 * so it NEVER overlaps the Android notification/status bar or camera cutout.
 * Uses `FlowRow` so the title, current tab pill, and action icons wrap cleanly at any font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedoGlassTopBar(
    currentTab: MainNavTab,
    onOpenQrScanner: () -> Unit,
    onOpenPackManager: () -> Unit,
    onOpenPdfExport: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    Surface(
        color = tokens.surfaceTint.copy(alpha = tokens.surfaceAlpha),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
            .wrapContentHeight()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = tokens.borderAlpha), RoundedCornerShape(22.dp))
    ) {
        FlowRow(
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Medo Next",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.accent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                MedoPill(text = currentTab.labelFa)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenQrScanner,
                    modifier = Modifier.testTag("top_qr_scanner_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "اسکن کد QR رایانه",
                        tint = tokens.accent
                    )
                }
                IconButton(
                    onClick = onOpenPackManager,
                    modifier = Modifier.testTag("top_pack_manager_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "مدیریت بسته‌ها و انتقال",
                        tint = tokens.textPrimary
                    )
                }
                IconButton(
                    onClick = onOpenPdfExport,
                    modifier = Modifier.testTag("top_pdf_export_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "خروجی PDF",
                        tint = tokens.textPrimary
                    )
                }
                IconButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.testTag("top_diagnostics_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "گزارش و عیب‌یابی",
                        tint = tokens.textPrimary
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("top_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "تنظیمات",
                        tint = tokens.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MedoGlassBottomBar(
    currentTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit
) {
    val tokens = LocalMedoTheme.current
    val items: List<Pair<MainNavTab, ImageVector>> = listOf(
        MainNavTab.HOME to Icons.Default.Home,
        MainNavTab.PRACTICE to Icons.AutoMirrored.Filled.MenuBook,
        MainNavTab.EXAM to Icons.AutoMirrored.Filled.Assignment,
        MainNavTab.REVIEW to Icons.Default.Autorenew,
        MainNavTab.ANALYTICS to Icons.Default.Analytics
    )

    Surface(
        color = tokens.surfaceTint.copy(alpha = (tokens.surfaceAlpha + 0.1f).coerceAtMost(0.95f)),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .wrapContentHeight()
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = tokens.borderAlpha), RoundedCornerShape(26.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp)
        ) {
            items.forEach { (tab, icon) ->
                val selected = currentTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) tokens.accent.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("bottom_nav_${tab.name}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.labelFa,
                        tint = if (selected) tokens.accent else tokens.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = tab.labelFa,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) tokens.accent else tokens.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun filterAndSortQuestions(state: MedoUiState): List<QuestionEntity> {
    val nodeIdsToMatch = if (state.selectedNodeId != null) {
        val byParent = state.categoryNodes.groupBy { it.parentId }
        val collected = mutableSetOf<String>()
        fun collect(id: String) {
            collected.add(id)
            byParent[id]?.forEach { collect(it.id) }
        }
        collect(state.selectedNodeId)
        collected
    } else {
        emptySet()
    }

    val allowedQuestionIds = if (nodeIdsToMatch.isNotEmpty()) {
        state.crossRefs.filter { it.nodeId in nodeIdsToMatch }.map { it.siteQuestionId }.toSet()
    } else {
        state.allQuestions.map { it.siteQuestionId }.toSet()
    }

    val normalizedNodeQuery = PersianTextUtils.normalizeForSearch(state.nodeSearchQuery)

    val filtered = state.allQuestions.filter { q ->
        if (state.activeQuestionFilter != QuestionFilter.TRAP &&
            state.activeQuestionFilter != QuestionFilter.INCOMPLETE &&
            q.siteQuestionId !in allowedQuestionIds
        ) {
            return@filter false
        }

        if (normalizedNodeQuery.isNotBlank() && !q.normalizedSearchText.contains(normalizedNodeQuery)) {
            return@filter false
        }

        val prog = state.progressByQuestionId[q.siteQuestionId]
        when (state.activeQuestionFilter) {
            QuestionFilter.ALL -> true
            QuestionFilter.UNANSWERED -> prog?.lastSelectedOption == null
            QuestionFilter.WRONG -> prog?.isCorrect == false
            QuestionFilter.BOOKMARKED -> prog?.bookmarked == true
            QuestionFilter.STARRED -> prog?.starred == true
            QuestionFilter.FLAGGED -> prog?.flagged == true
            QuestionFilter.WITH_LESSON -> q.hasLesson
            QuestionFilter.WITH_MINDMAP -> q.hasMindMap
            QuestionFilter.TRAP -> q.isTrap
            QuestionFilter.INCOMPLETE -> !q.hasKey || !q.hasLesson || !q.hasMindMap
        }
    }

    return when (state.activeSortOrder) {
        QuestionSortOrder.ORIGINAL -> filtered.sortedBy { it.orderInNode }
        QuestionSortOrder.RANDOM -> filtered.shuffled()
    }
}
