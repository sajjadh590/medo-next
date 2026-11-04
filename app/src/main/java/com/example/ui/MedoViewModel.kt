package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.CategoryNodeEntity
import com.example.data.ClozeCardEntity
import com.example.data.ExamSessionEntity
import com.example.data.InstalledPackEntity
import com.example.data.MediaBlobEntity
import com.example.data.MedoDatabase
import com.example.data.QuestionEntity
import com.example.data.QuestionNodeCrossRef
import com.example.data.SyntheticBankSeedAndImporter
import com.example.data.UserProgressEntity
import com.example.domain.ExamScorer
import com.example.domain.PackDeltaPlanner
import com.example.domain.ProgressBackupManager
import com.example.domain.Sm2Scheduler
import com.example.pdf.MedoPdfEngine
import com.example.pdf.PdfExportConfig
import com.example.qr.MedoQrAndLanEngine
import com.example.ui.theme.GlassEffectMode
import com.example.ui.theme.MedoFontChoice
import com.example.ui.theme.MedoSettingsState
import com.example.ui.theme.MedoThemeId
import com.example.ui.theme.TextSizePreset
import com.example.util.DiagnosticsLogger
import com.example.util.PersianTextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class MainNavTab(val labelFa: String) {
    HOME("خانه"),
    PRACTICE("تمرین"),
    EXAM("آزمون"),
    REVIEW("مرور"),
    ANALYTICS("آمار")
}

enum class OverlayModal {
    NONE,
    SETTINGS,
    PACK_MANAGER,
    QR_SCANNER,
    DIAGNOSTICS,
    PDF_EXPORT,
    LESSON_READER,
    MIND_MAP_VIEWER,
    NODE_QUESTION_LIST
}

enum class QuestionFilter(val labelFa: String) {
    ALL("همه"),
    UNANSWERED("بی‌پاسخ"),
    WRONG("غلط‌ها"),
    BOOKMARKED("نشان‌شده"),
    STARRED("ستاره‌دار"),
    FLAGGED("پرچم‌دار"),
    WITH_LESSON("دارای درسنامه"),
    WITH_MINDMAP("دارای مایندمپ"),
    TRAP("سوالات دام‌دار"),
    INCOMPLETE("فقط سوال‌های ناقص")
}

enum class QuestionSortOrder(val labelFa: String) {
    ORIGINAL("ترتیب اصلی"),
    RANDOM("تصادفی")
}

data class ActiveExamState(
    val title: String,
    val questionIds: List<String>,
    val currentQuestionIndex: Int = 0,
    val selectedOptionsByQuestionId: Map<String, Int> = emptyMap(),
    val flaggedQuestionIds: Set<String> = emptySet(),
    val negativeMarkingDivisor: Int = 3,
    val durationLimitSeconds: Int = 1200,
    val elapsedSeconds: Int = 0,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val scoreBreakdown: ExamScorer.ScoreBreakdown? = null
)

data class MedoUiState(
    val isInitialized: Boolean = false,
    val currentTab: MainNavTab = MainNavTab.HOME,
    val activeModal: OverlayModal = OverlayModal.NONE,
    val settings: MedoSettingsState = MedoSettingsState(),

    // Home & Tree state
    val onlyDownloadedTreeTab: Boolean = true,
    val expandedNodeIds: Set<String> = setOf("cat_dastyari", "cat_dastyari_internal", "cat_pre_intern", "cat_talifi"),
    val globalSearchQuery: String = "",
    val searchResults: List<QuestionEntity> = emptyList(),
    val lastSearchLatencyMs: Long = 0L,

    // Database collections
    val categoryNodes: List<CategoryNodeEntity> = emptyList(),
    val allQuestions: List<QuestionEntity> = emptyList(),
    val crossRefs: List<QuestionNodeCrossRef> = emptyList(),
    val progressByQuestionId: Map<String, UserProgressEntity> = emptyMap(),
    val clozeCards: List<ClozeCardEntity> = emptyList(),
    val examHistory: List<ExamSessionEntity> = emptyList(),
    val installedPacks: List<InstalledPackEntity> = emptyList(),
    val mediaBlobs: List<MediaBlobEntity> = emptyList(),

    // Node Question List & Practice State
    val selectedNodeId: String? = "cat_dastyari",
    val activeQuestionFilter: QuestionFilter = QuestionFilter.ALL,
    val activeSortOrder: QuestionSortOrder = QuestionSortOrder.ORIGINAL,
    val nodeSearchQuery: String = "",
    val currentPracticeQuestionId: String? = "q_1001",
    val immediateFeedbackMode: Boolean = true,
    val revealedInEndModeIds: Set<String> = emptySet(),
    val questionOpenedTimestampMs: Long = System.currentTimeMillis(),

    // Exam Simulator
    val activeExam: ActiveExamState? = null,

    // Mind-map & Lesson Reader target
    val activeMindMapHash: String = "mm_demo_diagram_01",
    val activeMindMapTitle: String = "[نمونه آزمایشی] نمودار درختی آزمایشی ۱",

    // Pack Transfer & PDF status banner
    val connectedLanServerUrl: String = "http://192.168.1.100:8765",
    val statusBannerMessage: String? = null,
    val lastPdfPlanResult: MedoPdfEngine.PdfPlanResult? = null,
    val lastExportedPdfPath: String? = null
)

class MedoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MedoDatabase.getInstance(application)
    private val dao = db.medoDao()
    private val prefs = application.getSharedPreferences("medo_next_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MedoUiState())
    val uiState: StateFlow<MedoUiState> = _uiState.asStateFlow()

    val todayEpochDay: Long = 20762L

    init {
        DiagnosticsLogger.installCrashHandler(application)
        DiagnosticsLogger.logEvent("INIT", "راه‌اندازی موتور داده و رابط شیشه‌ای Medo Next")
        loadPersistedPreferences()
        observeDatabaseStreams()
        seedDatabaseIfDebugOnly()
        runDailyAutoBackupIfDue(application)
    }

    private fun loadPersistedPreferences() {
        val themeId = MedoThemeId.fromId(prefs.getString("theme_id", MedoThemeId.DARK.id) ?: MedoThemeId.DARK.id)
        val followSystem = prefs.getBoolean("follow_system_theme", false)
        val glassMode = GlassEffectMode.entries.find {
            it.name == prefs.getString("glass_mode", GlassEffectMode.AUTO.name)
        } ?: GlassEffectMode.AUTO
        val reduceMotion = prefs.getBoolean("reduce_motion", false)
        val usePersianDigits = prefs.getBoolean("persian_digits", true)
        val textSizePreset = TextSizePreset.entries.find {
            it.name == prefs.getString("text_size", TextSizePreset.MEDIUM.name)
        } ?: TextSizePreset.MEDIUM
        val lineSpacing = prefs.getFloat("line_spacing", 1.0f)
        val fontChoice = MedoFontChoice.fromId(prefs.getString("font_choice", MedoFontChoice.VAZIRMATN.id))
        val haptics = prefs.getBoolean("enable_haptics", true)
        val swipe = prefs.getBoolean("enable_swipe", true)
        val dailyLimit = prefs.getInt("daily_new_cards", 20)
        val dailyAutoBackup = prefs.getBoolean("daily_auto_backup_docs", true)
        val expandedRaw = prefs.getStringSet(
            "expanded_node_ids",
            setOf("cat_dastyari", "cat_dastyari_internal", "cat_pre_intern", "cat_talifi")
        ).orEmpty()
        val lastPracticeQ = prefs.getString("last_practice_q", "q_1001")

        _uiState.update { state ->
            state.copy(
                settings = MedoSettingsState(
                    selectedThemeId = themeId,
                    followSystemTheme = followSystem,
                    glassEffectMode = glassMode,
                    reduceMotion = reduceMotion,
                    usePersianDigits = usePersianDigits,
                    textSizePreset = textSizePreset,
                    lineSpacingMultiplier = lineSpacing,
                    fontChoice = fontChoice,
                    enableHaptics = haptics,
                    enableSwipeGestures = swipe,
                    dailyNewCardsLimit = dailyLimit,
                    dailyAutoBackupToDocuments = dailyAutoBackup
                ),
                expandedNodeIds = expandedRaw,
                currentPracticeQuestionId = lastPracticeQ
            )
        }
    }

    private fun observeDatabaseStreams() {
        viewModelScope.launch {
            combine(
                dao.observeAllCategoryNodes(),
                dao.observeAllQuestions(),
                dao.observeAllCrossRefs(),
                dao.observeAllProgress(),
                dao.observeActiveClozeCards()
            ) { nodes, questions, refs, progressList, cloze ->
                Quintuple(nodes, questions, refs, progressList, cloze)
            }.collect { (nodes, questions, refs, progressList, cloze) ->
                val progressMap = progressList.associateBy { it.siteQuestionId }
                _uiState.update { current ->
                    val resolvedPracticeId = current.currentPracticeQuestionId
                        ?.takeIf { id -> questions.any { it.siteQuestionId == id } }
                        ?: questions.firstOrNull()?.siteQuestionId
                    current.copy(
                        isInitialized = true,
                        categoryNodes = nodes,
                        allQuestions = questions,
                        crossRefs = refs,
                        progressByQuestionId = progressMap,
                        clozeCards = cloze,
                        currentPracticeQuestionId = resolvedPracticeId
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                dao.observeExamSessions(),
                dao.observeInstalledPacks(),
                dao.observeMediaBlobs()
            ) { exams, packs, media ->
                Triple(exams, packs, media)
            }.collect { (exams, packs, media) ->
                _uiState.update { current ->
                    current.copy(
                        examHistory = exams,
                        installedPacks = packs,
                        mediaBlobs = media
                    )
                }
            }
        }
    }

    /**
     * Section B Rule: Release builds start empty and import user `.medo` files;
     * synthetic demo questions are only seeded in debug/test builds and explicitly labelled «[نمونه آزمایشی]».
     */
    private fun seedDatabaseIfDebugOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!SyntheticBankSeedAndImporter.shouldSeedDemoData(BuildConfig.DEBUG)) {
                DiagnosticsLogger.logEvent("RELEASE_EMPTY", "نسخه نهایی (Release): شروع با بانک خالی و آماده واردسازی فایل .medo")
                return@launch
            }
            val existingQuestions = dao.getAllQuestions()
            if (existingQuestions.isEmpty()) {
                val startMs = System.currentTimeMillis()
                val bundle = SyntheticBankSeedAndImporter.buildInitialClinicalSeed(todayEpochDay)
                dao.atomicImportBankChunk(
                    nodes = bundle.nodes,
                    questions = bundle.questions,
                    crossRefs = bundle.crossRefs,
                    packs = bundle.packs,
                    mediaBlobs = bundle.mediaBlobs,
                    clozeCards = bundle.clozeCards
                )
                dao.upsertProgress(
                    UserProgressEntity(
                        siteQuestionId = "q_1001",
                        contentHash = bundle.questions.first().contentHash,
                        lastSelectedOption = 1,
                        isCorrect = true,
                        attemptsCount = 2,
                        wrongCount = 0,
                        bookmarked = true,
                        starred = true,
                        flagged = false,
                        personalNote = "[نمونه آزمایشی] یادداشت نمونه برای سوال ۱۰۰۱",
                        totalTimeSpentMs = 34_000L,
                        lastAnsweredAt = System.currentTimeMillis() - 3_600_000L,
                        sm2Repetitions = 1,
                        sm2EasinessFactor = 2.5f,
                        sm2IntervalDays = 1,
                        sm2DueEpochDay = todayEpochDay
                    )
                )
                val elapsed = System.currentTimeMillis() - startMs
                DiagnosticsLogger.logEvent("SEED_DEMO", "بانک آزمایشی Debug با ${bundle.questions.size} سوال در ${elapsed}ms بارگذاری شد")
            }
        }
    }

    /**
     * Section E: Optional daily automatic progress export to the Documents folder (`Documents/MedoNext/`).
     */
    private fun runDailyAutoBackupIfDue(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_uiState.value.settings.dailyAutoBackupToDocuments) return@launch
            val lastBackupEpochDay = prefs.getLong("last_auto_backup_epoch_day", 0L)
            if (todayEpochDay > lastBackupEpochDay) {
                performDailyAutoBackupToDocuments(context)
                prefs.edit().putLong("last_auto_backup_epoch_day", todayEpochDay).apply()
            }
        }
    }

    suspend fun performDailyAutoBackupToDocuments(context: Context): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            val allProgress = dao.getAllProgress()
            val json = ProgressBackupManager.exportProgressToJson(allProgress)
            val docsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: File(context.filesDir, "Documents")
            val medoDocsDir = File(docsRoot, "MedoNext")
            medoDocsDir.mkdirs()
            val outFile = File(medoDocsDir, "medo-progress-daily-$todayEpochDay.json")
            outFile.writeText(json)
            DiagnosticsLogger.logEvent("AUTO_BACKUP", "پشتیبان خودکار روزانه در Documents/MedoNext ذخیره شد (${allProgress.size} رکورد)")
            outFile
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("AUTO_BACKUP_ERR", "خطا در پشتیبان خودکار روزانه: ${e.javaClass.simpleName}")
            null
        }
    }

    // Navigation & Modals
    fun selectTab(tab: MainNavTab) {
        _uiState.update {
            it.copy(
                currentTab = tab,
                activeModal = OverlayModal.NONE,
                questionOpenedTimestampMs = System.currentTimeMillis()
            )
        }
    }

    fun openModal(modal: OverlayModal) {
        _uiState.update { it.copy(activeModal = modal) }
    }

    fun closeModal() {
        _uiState.update { it.copy(activeModal = OverlayModal.NONE) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun onQrLanUrlValidatedAndConnected(validatedUrl: String) {
        DiagnosticsLogger.logEvent("LAN_CONNECT", "اتصال تاییدشده به سرور محلی: $validatedUrl")
        _uiState.update {
            it.copy(
                connectedLanServerUrl = validatedUrl,
                activeModal = OverlayModal.PACK_MANAGER,
                statusBannerMessage = "اتصال به آدرس محلی $validatedUrl برقرار شد. بسته‌های مورد نظر را انتخاب کنید."
            )
        }
    }

    // Tree actions
    fun setOnlyDownloadedTreeTab(onlyDownloaded: Boolean) {
        _uiState.update { it.copy(onlyDownloadedTreeTab = onlyDownloaded) }
    }

    fun toggleNodeExpansion(nodeId: String) {
        _uiState.update { current ->
            val updated = if (nodeId in current.expandedNodeIds) {
                current.expandedNodeIds - nodeId
            } else {
                current.expandedNodeIds + nodeId
            }
            prefs.edit().putStringSet("expanded_node_ids", updated).apply()
            current.copy(expandedNodeIds = updated)
        }
    }

    fun expandAllNodes() {
        _uiState.update { current ->
            val allIds = current.categoryNodes.map { it.id }.toSet()
            prefs.edit().putStringSet("expanded_node_ids", allIds).apply()
            current.copy(expandedNodeIds = allIds)
        }
    }

    fun collapseAllNodes() {
        prefs.edit().putStringSet("expanded_node_ids", emptySet()).apply()
        _uiState.update { it.copy(expandedNodeIds = emptySet()) }
    }

    fun openNodeQuestionList(nodeId: String) {
        _uiState.update {
            it.copy(
                selectedNodeId = nodeId,
                activeModal = OverlayModal.NODE_QUESTION_LIST
            )
        }
    }

    // Search
    fun updateGlobalSearch(query: String) {
        _uiState.update { it.copy(globalSearchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), lastSearchLatencyMs = 0L) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val normalized = PersianTextUtils.normalizeForSearch(query)
            val matches = dao.searchQuestions(normalized)
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            _uiState.update {
                it.copy(
                    searchResults = matches,
                    lastSearchLatencyMs = latency
                )
            }
        }
    }

    // Practice & Question Filter actions
    fun setQuestionFilter(filter: QuestionFilter) {
        _uiState.update { it.copy(activeQuestionFilter = filter) }
    }

    fun setSortOrder(order: QuestionSortOrder) {
        _uiState.update { it.copy(activeSortOrder = order) }
    }

    fun setNodeSearchQuery(query: String) {
        _uiState.update { it.copy(nodeSearchQuery = query) }
    }

    fun selectQuestionForPractice(questionId: String) {
        prefs.edit().putString("last_practice_q", questionId).apply()
        _uiState.update {
            it.copy(
                currentPracticeQuestionId = questionId,
                currentTab = MainNavTab.PRACTICE,
                activeModal = OverlayModal.NONE,
                questionOpenedTimestampMs = System.currentTimeMillis()
            )
        }
    }

    fun toggleImmediateFeedbackMode() {
        _uiState.update {
            it.copy(
                immediateFeedbackMode = !it.immediateFeedbackMode,
                revealedInEndModeIds = emptySet()
            )
        }
    }

    fun revealAnswerForCurrentQuestion(questionId: String) {
        _uiState.update {
            it.copy(revealedInEndModeIds = it.revealedInEndModeIds + questionId)
        }
    }

    fun submitPracticeOption(question: QuestionEntity, optionIndex: Int) {
        val now = System.currentTimeMillis()
        val elapsedMs = (now - _uiState.value.questionOpenedTimestampMs).coerceIn(500L, 300_000L)
        val isCorrect = question.correctOptionIndex != null && question.correctOptionIndex == optionIndex

        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getProgressForQuestion(question.siteQuestionId)
                ?: UserProgressEntity(siteQuestionId = question.siteQuestionId, contentHash = question.contentHash)

            val updated = existing.copy(
                lastSelectedOption = optionIndex,
                isCorrect = isCorrect,
                attemptsCount = existing.attemptsCount + 1,
                wrongCount = if (!isCorrect) existing.wrongCount + 1 else existing.wrongCount,
                totalTimeSpentMs = existing.totalTimeSpentMs + elapsedMs,
                lastAnsweredAt = now,
                sm2DueEpochDay = if (existing.sm2DueEpochDay == 0L) todayEpochDay else existing.sm2DueEpochDay
            )
            dao.upsertProgress(updated)
            DiagnosticsLogger.logEvent("PRACTICE", "ثبت پاسخ برای ${question.siteQuestionId} (صحیح=$isCorrect)")
        }
    }

    fun toggleBookmark(questionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getProgressForQuestion(questionId)
                ?: UserProgressEntity(siteQuestionId = questionId)
            dao.upsertProgress(existing.copy(bookmarked = !existing.bookmarked))
        }
    }

    fun toggleStar(questionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getProgressForQuestion(questionId)
                ?: UserProgressEntity(siteQuestionId = questionId)
            dao.upsertProgress(existing.copy(starred = !existing.starred))
        }
    }

    fun toggleFlag(questionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getProgressForQuestion(questionId)
                ?: UserProgressEntity(siteQuestionId = questionId)
            dao.upsertProgress(existing.copy(flagged = !existing.flagged))
        }
    }

    fun savePersonalNote(questionId: String, noteText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getProgressForQuestion(questionId)
                ?: UserProgressEntity(siteQuestionId = questionId)
            dao.upsertProgress(existing.copy(personalNote = noteText.trim()))
            _uiState.update { it.copy(statusBannerMessage = "یادداشت شخصی ذخیره شد") }
        }
    }

    fun addHighlightToQuestion(questionId: String, phrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getProgressForQuestion(questionId)
                ?: UserProgressEntity(siteQuestionId = questionId)
            val currentList = PackDeltaPlanner.parseJsonStringArray(existing.highlightsJson).toMutableList()
            if (phrase !in currentList) {
                currentList.add(phrase)
            }
            dao.upsertProgress(existing.copy(highlightsJson = JSONArray(currentList).toString()))
        }
    }

    fun openMindMapForQuestion(question: QuestionEntity) {
        val hash = question.mindMapMediaHash ?: "mm_demo_diagram_01"
        val blob = _uiState.value.mediaBlobs.find { it.mediaHash == hash }
        _uiState.update {
            it.copy(
                activeMindMapHash = hash,
                activeMindMapTitle = blob?.titleFa ?: "مایندمپ ${question.siteQuestionId}",
                activeModal = OverlayModal.MIND_MAP_VIEWER
            )
        }
    }

    // SM-2 Spaced Repetition rating (Questions & Cloze cards)
    fun rateQuestionSm2(questionId: String, rating: Sm2Scheduler.Rating) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getProgressForQuestion(questionId)
                ?: UserProgressEntity(siteQuestionId = questionId)
            val next = Sm2Scheduler.schedule(
                currentRepetitions = existing.sm2Repetitions,
                currentEf = existing.sm2EasinessFactor,
                currentIntervalDays = existing.sm2IntervalDays,
                rating = rating,
                todayEpochDay = todayEpochDay
            )
            dao.upsertProgress(
                existing.copy(
                    sm2Repetitions = next.repetitions,
                    sm2EasinessFactor = next.easinessFactor,
                    sm2IntervalDays = next.intervalDays,
                    sm2DueEpochDay = next.dueEpochDay
                )
            )
            DiagnosticsLogger.logEvent("SM2", "زمان‌بندی مجدد سوال $questionId با امتیاز ${rating.name}: فاصله ${next.intervalDays} روز")
        }
    }

    fun rateClozeCardSm2(card: ClozeCardEntity, rating: Sm2Scheduler.Rating) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = Sm2Scheduler.schedule(
                currentRepetitions = card.repetitions,
                currentEf = card.easinessFactor,
                currentIntervalDays = card.intervalDays,
                rating = rating,
                todayEpochDay = todayEpochDay
            )
            dao.upsertClozeCard(
                card.copy(
                    repetitions = next.repetitions,
                    easinessFactor = next.easinessFactor,
                    intervalDays = next.intervalDays,
                    dueEpochDay = next.dueEpochDay
                )
            )
        }
    }

    fun suppressClozeCard(card: ClozeCardEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.upsertClozeCard(card.copy(suppressed = true))
        }
    }

    // Exam Simulator actions
    fun startNewExam(
        title: String,
        questionPool: List<QuestionEntity>,
        count: Int,
        durationMinutes: Int,
        negativeMarkingDivisor: Int
    ) {
        val chosen = questionPool.take(count.coerceAtLeast(1)).map { it.siteQuestionId }
        if (chosen.isEmpty()) return
        _uiState.update {
            it.copy(
                activeExam = ActiveExamState(
                    title = title,
                    questionIds = chosen,
                    currentQuestionIndex = 0,
                    negativeMarkingDivisor = negativeMarkingDivisor,
                    durationLimitSeconds = durationMinutes * 60
                ),
                currentTab = MainNavTab.EXAM
            )
        }
        DiagnosticsLogger.logEvent("EXAM", "شروع آزمون «$title» با ${chosen.size} سوال و نمره منفی 1/$negativeMarkingDivisor")
    }

    fun selectExamAnswer(questionId: String, optionIndex: Int) {
        _uiState.update { state ->
            val exam = state.activeExam ?: return@update state
            if (exam.isFinished) return@update state
            val updatedMap = exam.selectedOptionsByQuestionId.toMutableMap()
            if (updatedMap[questionId] == optionIndex) {
                updatedMap.remove(questionId)
            } else {
                updatedMap[questionId] = optionIndex
            }
            state.copy(activeExam = exam.copy(selectedOptionsByQuestionId = updatedMap))
        }
    }

    fun toggleExamQuestionFlag(questionId: String) {
        _uiState.update { state ->
            val exam = state.activeExam ?: return@update state
            val updatedFlags = if (questionId in exam.flaggedQuestionIds) {
                exam.flaggedQuestionIds - questionId
            } else {
                exam.flaggedQuestionIds + questionId
            }
            state.copy(activeExam = exam.copy(flaggedQuestionIds = updatedFlags))
        }
    }

    fun jumpToExamQuestion(index: Int) {
        _uiState.update { state ->
            val exam = state.activeExam ?: return@update state
            state.copy(activeExam = exam.copy(currentQuestionIndex = index.coerceIn(0, exam.questionIds.lastIndex)))
        }
    }

    fun toggleExamPause() {
        _uiState.update { state ->
            val exam = state.activeExam ?: return@update state
            state.copy(activeExam = exam.copy(isPaused = !exam.isPaused))
        }
    }

    fun finishActiveExam() {
        val state = _uiState.value
        val exam = state.activeExam ?: return
        val questionsById = state.allQuestions.associateBy { it.siteQuestionId }

        var correct = 0
        var wrong = 0
        for (qId in exam.questionIds) {
            val chosen = exam.selectedOptionsByQuestionId[qId] ?: continue
            val q = questionsById[qId] ?: continue
            if (q.correctOptionIndex != null) {
                if (chosen == q.correctOptionIndex) correct++ else wrong++
            }
        }

        val breakdown = ExamScorer.calculateScore(
            totalQuestions = exam.questionIds.size,
            correctCount = correct,
            wrongCount = wrong,
            negativeMarkingDivisor = exam.negativeMarkingDivisor
        )

        _uiState.update {
            it.copy(
                activeExam = exam.copy(
                    isFinished = true,
                    scoreBreakdown = breakdown
                )
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            dao.upsertExamSession(
                ExamSessionEntity(
                    title = exam.title,
                    scopeNodeId = state.selectedNodeId,
                    createdAt = System.currentTimeMillis(),
                    finishedAt = System.currentTimeMillis(),
                    durationLimitSeconds = exam.durationLimitSeconds,
                    elapsedSeconds = exam.elapsedSeconds,
                    negativeMarkingDivisor = exam.negativeMarkingDivisor,
                    questionIdsJson = JSONArray(exam.questionIds).toString(),
                    answersMapJson = JSONObject(exam.selectedOptionsByQuestionId).toString(),
                    flaggedIdsJson = JSONArray(exam.flaggedQuestionIds.toList()).toString(),
                    rawScorePercent = breakdown.netPercentWithNegative,
                    correctCount = breakdown.correctCount,
                    wrongCount = breakdown.wrongCount,
                    unansweredCount = breakdown.unansweredCount,
                    isCompleted = true
                )
            )
            DiagnosticsLogger.logEvent("EXAM_END", "پایان آزمون: تراز ${breakdown.netPercentWithNegative}٪ (صحیح=$correct، غلط=$wrong)")
        }
    }

    fun dismissActiveExam() {
        _uiState.update { it.copy(activeExam = null) }
    }

    // Settings updates
    fun updateSettings(transform: (MedoSettingsState) -> MedoSettingsState) {
        _uiState.update { state ->
            val next = transform(state.settings)
            prefs.edit()
                .putString("theme_id", next.selectedThemeId.id)
                .putBoolean("follow_system_theme", next.followSystemTheme)
                .putString("glass_mode", next.glassEffectMode.name)
                .putBoolean("reduce_motion", next.reduceMotion)
                .putBoolean("persian_digits", next.usePersianDigits)
                .putString("text_size", next.textSizePreset.name)
                .putFloat("line_spacing", next.lineSpacingMultiplier)
                .putString("font_choice", next.fontChoice.id)
                .putBoolean("enable_haptics", next.enableHaptics)
                .putBoolean("enable_swipe", next.enableSwipeGestures)
                .putInt("daily_new_cards", next.dailyNewCardsLimit)
                .putBoolean("daily_auto_backup_docs", next.dailyAutoBackupToDocuments)
                .apply()
            state.copy(settings = next)
        }
    }

    // PDF Export & Share
    fun generateAndSharePdf(context: Context, config: PdfExportConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            val questions = _uiState.value.allQuestions
            val exportsDir = File(context.cacheDir, "exports")
            exportsDir.mkdirs()
            val pdfFile = File(exportsDir, "medo_next_export_${config.mode.name.lowercase()}.pdf")
            val plan = MedoPdfEngine.renderToFile(context, questions, config, pdfFile)
            DiagnosticsLogger.logEvent(
                "PDF_EXPORT",
                "خروجی PDF (${config.mode.labelFa}) با ${plan.totalPageCount} صفحه ساخته شد"
            )
            _uiState.update {
                it.copy(
                    lastPdfPlanResult = plan,
                    lastExportedPdfPath = pdfFile.absolutePath,
                    statusBannerMessage = "فایل PDF با موفقیت ساخته شد (${plan.totalPageCount} صفحه A4)"
                )
            }
            withContext(Dispatchers.Main) {
                shareExportedFile(context, pdfFile, "application/pdf", "اشتراک‌گذاری فایل PDF آزمون")
            }
        }
    }

    // Progress JSON Export & Import
    fun exportAndShareProgressJson(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val allProgress = dao.getAllProgress()
            val json = ProgressBackupManager.exportProgressToJson(allProgress)
            val exportsDir = File(context.cacheDir, "exports")
            exportsDir.mkdirs()
            val jsonFile = File(exportsDir, "medo_progress_backup.json")
            jsonFile.writeText(json)
            // Also write to Documents/MedoNext if enabled
            performDailyAutoBackupToDocuments(context)
            DiagnosticsLogger.logEvent("PROGRESS_EXPORT", "پشتیبان پیشرفت (${allProgress.size} رکورد) صادر شد")
            _uiState.update {
                it.copy(statusBannerMessage = "پشتیبان پیشرفت (${allProgress.size} رکورد) آماده اشتراک‌گذاری و ذخیره در Documents شد")
            }
            withContext(Dispatchers.Main) {
                shareExportedFile(context, jsonFile, "application/json", "پشتیبان پیشرفت و یادداشت‌های Medo Next")
            }
        }
    }

    fun importProgressJsonString(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingMap = dao.getAllProgress().associateBy { it.siteQuestionId }
                val merged = ProgressBackupManager.importProgressFromJson(jsonString, existingMap)
                dao.upsertProgressList(merged)
                DiagnosticsLogger.logEvent("PROGRESS_IMPORT", "ادغام موفق ${merged.size} رکورد پیشرفت کاربر")
                _uiState.update {
                    it.copy(statusBannerMessage = "بازیابی و ادغام موفق ${merged.size} رکورد پیشرفت و یادداشت")
                }
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent("PROGRESS_ERR", "خطا در خواندن فایل پیشرفت: ${e.message}")
                _uiState.update {
                    it.copy(statusBannerMessage = "خطا در ساختار فایل JSON پشتیبان")
                }
            }
        }
    }

    // Pack Manager & v3 Delta Update
    fun applySimulatedV3DeltaUpdate(mirrorMode: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val sampleManifestJson = """
                {
                  "formatVersion": 3,
                  "bankVersion": "2026.11.2-delta",
                  "packs": [
                    {
                      "id": "pack_pre_intern_text",
                      "categoryId": "cat_pre_intern",
                      "categoryPath": "پره انترنی جامع (به‌روزرسانی دلتا)",
                      "level": "media",
                      "questionCount": 2,
                      "sizeBytes": 6800000,
                      "chunkHashes": ["chunk_pre_01", "chunk_pre_02_delta"],
                      "mediaHashes": ["mm_demo_diagram_01"]
                    }
                  ],
                  "questionRevisions": { "q_1004": "rev_2" },
                  "tombstones": ["q_1006"]
                }
            """.trimIndent()

            val manifest = PackDeltaPlanner.parseManifest(sampleManifestJson)
            val installed = dao.getInstalledPacks()
            val mediaHashes = _uiState.value.mediaBlobs.map { it.mediaHash }.toSet()
            val plan = PackDeltaPlanner.planDeltaUpdate(
                manifest = manifest,
                selectedPackIds = setOf("pack_pre_intern_text"),
                installedPacks = installed,
                existingMediaHashes = mediaHashes,
                mirrorMode = mirrorMode
            )

            if (plan.questionsToRemoveOrMark.isNotEmpty()) {
                if (plan.mirrorDestructiveDelete) {
                    dao.deleteQuestionsByIds(plan.questionsToRemoveOrMark)
                } else {
                    dao.markQuestionsRemovedUpstream(plan.questionsToRemoveOrMark)
                }
            }

            val updatedPacks = installed.map { p ->
                if (p.packId == "pack_pre_intern_text") {
                    p.copy(
                        version = manifest.bankVersion,
                        updateAvailable = false,
                        chunkHashesJson = "[\"chunk_pre_01\",\"chunk_pre_02_delta\"]"
                    )
                } else {
                    p
                }
            }
            dao.insertPacks(updatedPacks)

            DiagnosticsLogger.logEvent(
                "DELTA_SYNC",
                "به‌روزرسانی تفاضلی v3: ${plan.chunksToDownload.size} قطعه جدید، ${plan.chunksAlreadyCached.size} قطعه از کش"
            )
            _uiState.update {
                it.copy(
                    statusBannerMessage = "به‌روزرسانی دلتا v3 انجام شد (${plan.chunksToDownload.size} قطعه جدید دریافت شد، پیشرفت حفظ گردید)"
                )
            }
        }
    }

    fun deleteInstalledPack(packId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePackById(packId)
            DiagnosticsLogger.logEvent("PACK_DEL", "حذف بسته $packId (پیشرفت و یادداشت‌های کاربر حفظ شد)")
            _uiState.update {
                it.copy(statusBannerMessage = "بسته $packId حذف شد (پیشرفت و یادداشت‌های شما دست‌نخورده باقی ماند)")
            }
        }
    }

    private fun shareExportedFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("SHARE_INFO", "فایل در مسیر داخلی ذخیره شد: ${file.name}")
        }
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
