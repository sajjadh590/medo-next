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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.QuestionEntity
import com.example.data.UserProgressEntity
import com.example.domain.CategoryTreeFlattener
import com.example.pdf.MedoPdfEngine
import com.example.ui.MedoUiState
import com.example.ui.OverlayModal
import com.example.ui.QuestionFilter
import com.example.ui.QuestionSortOrder
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
import org.json.JSONArray

/**
 * 4.1 Home Screen (خانه):
 * - «ادامه از همانجا» resume card
 * - Today's SM-2 review due count & overall progress ring
 * - Storage / Installed Pack banner
 * - Global normalized search field (< 300ms)
 * - Category Tree with two tabs («بانک من» and «همه دسته‌ها»), «باز کردن همه / بستن همه»,
 *   full-path disambiguation where category names repeat, and separate >= 48dp question count pill target.
 */
@Composable
fun HomeScreen(
    state: MedoUiState,
    onContinuePractice: () -> Unit,
    onGoToReview: () -> Unit,
    onOpenPackManager: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectSearchResult: (String) -> Unit,
    onSwitchTreeTab: (Boolean) -> Unit,
    onToggleNodeExpand: (String) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onOpenNodeQuestionList: (String) -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current

    // Compute per-node question & answered counts
    val (nodeQuestionCounts, nodeAnsweredCounts) = remember(
        state.categoryNodes,
        state.crossRefs,
        state.progressByQuestionId
    ) {
        val qCounts = mutableMapOf<String, Int>()
        val aCounts = mutableMapOf<String, Int>()
        val refsByNode = state.crossRefs.groupBy { it.nodeId }
        for (node in state.categoryNodes) {
            val qIds = refsByNode[node.id]?.map { it.siteQuestionId }.orEmpty()
            qCounts[node.id] = if (qIds.isNotEmpty()) qIds.size else node.totalBankQuestionsCount
            aCounts[node.id] = qIds.count { state.progressByQuestionId[it]?.lastSelectedOption != null }
        }
        qCounts to aCounts
    }

    val flattenedTree = remember(
        state.categoryNodes,
        state.expandedNodeIds,
        state.onlyDownloadedTreeTab,
        nodeQuestionCounts,
        nodeAnsweredCounts
    ) {
        CategoryTreeFlattener.flattenTree(
            allNodes = state.categoryNodes,
            expandedNodeIds = state.expandedNodeIds,
            onlyDownloadedTab = state.onlyDownloadedTreeTab,
            nodeQuestionCounts = nodeQuestionCounts,
            nodeAnsweredCounts = nodeAnsweredCounts
        )
    }

    val totalQuestions = state.allQuestions.size.coerceAtLeast(1)
    val answeredQuestions = state.progressByQuestionId.values.count { it.lastSelectedOption != null }
    val dueReviewsCount = state.progressByQuestionId.values.count { it.sm2DueEpochDay <= 20762L && !it.sm2Suppressed } +
        state.clozeCards.count { it.dueEpochDay <= 20762L && !it.suppressed }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Global Search Field
        item {
            OutlinedTextField(
                value = state.globalSearchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("جستجوی سریع در متن سوالات، گزینه‌ها، درسنامه‌ها و داروها...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "جستجو", tint = tokens.accent)
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_search_input")
            )
        }

        // Show fast normalized search results if query is active
        if (state.globalSearchQuery.isNotBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "نتایج جستجو (${PersianTextUtils.formatNumber(state.searchResults.size, settings.usePersianDigits)} مورد در ${PersianTextUtils.formatNumber(state.lastSearchLatencyMs, settings.usePersianDigits)} میلی‌ثانیه):",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.accent,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.searchResults.isEmpty()) {
                            Text(
                                text = "موردی یافت نشد. املای کلمه یا نام ژنریک دارو را بررسی کنید.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                        } else {
                            state.searchResults.forEach { q ->
                                val cleanStem = q.stemHtml.replace(Regex("<[^>]+>"), " ").trim()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectSearchResult(q.siteQuestionId) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cleanStem,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    MedoPill(text = q.siteQuestionId)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. «ادامه از همانجا» Card + Today's Review & Overall Progress
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("continue_where_left_off_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ادامه از همان‌جا",
                                style = MaterialTheme.typography.headlineMedium,
                                color = tokens.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "آخرین سوال فعال: ${state.currentPracticeQuestionId ?: "q_1001"} • مرور امروز: ${PersianTextUtils.formatNumber(dueReviewsCount, settings.usePersianDigits)} کارت",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                        }
                        MedoProgressRing(
                            progress = answeredQuestions.toFloat() / totalQuestions.toFloat(),
                            size = 56.dp,
                            strokeWidth = 5.dp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MedoPrimaryButton(
                            text = "ادامه تمرین بالینی",
                            onClick = onContinuePractice,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("continue_practice_btn")
                        )
                        MedoSecondaryButton(
                            text = "مرور امروز (${PersianTextUtils.formatNumber(dueReviewsCount, settings.usePersianDigits)})",
                            onClick = onGoToReview,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_review_due_btn")
                        )
                    }
                }
            }
        }

        // 3. Storage & Pack Status Banner
        item {
            GlassCard(
                onClick = onOpenPackManager,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_pack_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "وضعیت بسته‌های آفلاین (${PersianTextUtils.formatNumber(state.installedPacks.size, settings.usePersianDigits)} بسته نصب‌شده)",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "دستیاری پزشکی، پره انترنی، تالیفی • آماده دریافت به‌روزرسانی دلتا v3",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                    MedoPill(
                        text = if (state.installedPacks.any { it.updateAvailable }) "به‌روزرسانی موجود" else "مدیریت بسته‌ها",
                        containerColor = if (state.installedPacks.any { it.updateAvailable }) tokens.warning.copy(alpha = 0.2f) else tokens.accent.copy(alpha = 0.16f),
                        contentColor = if (state.installedPacks.any { it.updateAvailable }) tokens.warning else tokens.accent
                    )
                }
            }
        }

        // 4. Category Tree Header: Two Tabs («بانک من» / «همه دسته‌ها») + «باز کردن همه / بستن همه»
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MedoPill(
                                text = "بانک من",
                                containerColor = if (state.onlyDownloadedTreeTab) tokens.accent else tokens.surfaceTint,
                                contentColor = if (state.onlyDownloadedTreeTab) tokens.accentOn else tokens.textPrimary,
                                onClick = { onSwitchTreeTab(true) },
                                modifier = Modifier.testTag("tab_my_bank")
                            )
                            MedoPill(
                                text = "همه دسته‌ها",
                                containerColor = if (!state.onlyDownloadedTreeTab) tokens.accent else tokens.surfaceTint,
                                contentColor = if (!state.onlyDownloadedTreeTab) tokens.accentOn else tokens.textPrimary,
                                onClick = { onSwitchTreeTab(false) },
                                modifier = Modifier.testTag("tab_all_categories")
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MedoPill(
                                text = "باز کردن همه",
                                onClick = onExpandAll,
                                modifier = Modifier.testTag("expand_all_btn")
                            )
                            MedoPill(
                                text = "بستن همه",
                                onClick = onCollapseAll,
                                modifier = Modifier.testTag("collapse_all_btn")
                            )
                        }
                    }
                }
            }
        }

        // 5. Flattened Category Tree Rows
        items(flattenedTree, key = { it.node.id }) { row ->
            CategoryTreeRowItem(
                row = row,
                usePersianDigits = settings.usePersianDigits,
                onRowClick = {
                    if (row.hasChildren) {
                        onToggleNodeExpand(row.node.id)
                    } else {
                        onOpenNodeQuestionList(row.node.id)
                    }
                },
                onQuestionCountPillClick = {
                    onOpenNodeQuestionList(row.node.id)
                }
            )
        }
    }
}

@Composable
private fun CategoryTreeRowItem(
    row: CategoryTreeFlattener.FlattenedTreeRow,
    usePersianDigits: Boolean,
    onRowClick: () -> Unit,
    onQuestionCountPillClick: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val indentDp = (row.node.depth * 16).dp

    GlassCard(
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp)
            .testTag("tree_row_${row.node.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Expand / collapse chevron zone (>= 48dp touch target)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRowClick)
                    .semantics {
                        contentDescription = if (row.hasChildren) {
                            if (row.isExpanded) "بستن زیرشاخه ${row.node.name}" else "باز کردن زیرشاخه ${row.node.name}"
                        } else {
                            "باز کردن دسته ${row.node.name}"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (row.hasChildren) {
                    Icon(
                        imageVector = if (row.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = tokens.accent
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = tokens.textSecondary
                    )
                }
            }

            // Category Title (with full path disambiguation if name repeats across categories)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onRowClick)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = row.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    fontWeight = if (row.node.depth == 0) FontWeight.Bold else FontWeight.Medium
                )
                if (!row.node.isDownloaded) {
                    Text(
                        text = "بسته دانلود نشده (قابل انتخاب در مدیریت بسته‌ها)",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.warning
                    )
                }
            }

            // Progress ring
            MedoProgressRing(
                progress = row.progressFraction,
                size = 38.dp,
                strokeWidth = 3.5.dp,
                showPercentageText = false
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Separate 48dp touch target question count pill that opens the question list for this node
            MedoPill(
                text = "${PersianTextUtils.formatNumber(row.questionCount, usePersianDigits)} سوال",
                onClick = onQuestionCountPillClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("node_count_pill_${row.node.id}")
            )
        }
    }
}

/**
 * 4.2 Question List Screen for a Node:
 * - Filters: all, unanswered, wrong, bookmarked, starred, flagged, with lesson, with mind map, trap questions, incomplete («فقط سوال‌های ناقص»)
 * - Sort: original order, random
 * - Search inside the node
 * - Jump grid
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeQuestionListModal(
    state: MedoUiState,
    filteredQuestions: List<QuestionEntity>,
    onSelectFilter: (QuestionFilter) -> Unit,
    onSelectSort: (QuestionSortOrder) -> Unit,
    onNodeSearchChange: (String) -> Unit,
    onOpenQuestion: (String) -> Unit,
    onClose: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current
    val currentNode = state.categoryNodes.find { it.id == state.selectedNodeId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.gradientStart)
            .padding(16.dp)
            .testTag("node_question_list_modal")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_node_list_btn")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = tokens.textPrimary
                    )
                }
                Column {
                    Text(
                        text = currentNode?.fullPath ?: "فهرست سوالات",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${PersianTextUtils.formatNumber(filteredQuestions.size, settings.usePersianDigits)} سوال منطبق با فیلتر",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.textSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuestionSortOrder.entries.forEach { order ->
                    MedoPill(
                        text = order.labelFa,
                        containerColor = if (state.activeSortOrder == order) tokens.accent else tokens.surfaceTint,
                        contentColor = if (state.activeSortOrder == order) tokens.accentOn else tokens.textPrimary,
                        onClick = { onSelectSort(order) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = state.nodeSearchQuery,
            onValueChange = onNodeSearchChange,
            placeholder = { Text("جستجو در سوالات این مبحث...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(QuestionFilter.entries) { filter ->
                FilterChip(
                    selected = state.activeQuestionFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(filter.labelFa) },
                    modifier = Modifier.testTag("filter_chip_${filter.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Jump grid
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            filteredQuestions.forEachIndexed { idx, q ->
                val prog = state.progressByQuestionId[q.siteQuestionId]
                val badgeColor = when (prog?.isCorrect) {
                    true -> tokens.correct
                    false -> tokens.wrong
                    null -> tokens.surfaceTint
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor.copy(alpha = 0.25f))
                        .border(1.dp, badgeColor, RoundedCornerShape(10.dp))
                        .clickable { onOpenQuestion(q.siteQuestionId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = PersianTextUtils.formatNumber(idx + 1, settings.usePersianDigits),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Question list cards
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredQuestions, key = { it.siteQuestionId }) { q ->
                val cleanStem = q.stemHtml.replace(Regex("<[^>]+>"), " ").trim()
                GlassCard(
                    onClick = { onOpenQuestion(q.siteQuestionId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MedoPill(text = "کد: ${q.siteQuestionId}")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (q.isTrap) {
                                    MedoPill(
                                        text = "⚠ دام تستی",
                                        containerColor = tokens.warning.copy(alpha = 0.2f),
                                        contentColor = tokens.warning
                                    )
                                }
                                if (!q.hasLesson || !q.hasMindMap || !q.hasKey) {
                                    MedoPill(
                                        text = buildString {
                                            append("ناقص: ")
                                            val missing = mutableListOf<String>()
                                            if (!q.hasKey) missing.add("کلید")
                                            if (!q.hasLesson) missing.add("درسنامه")
                                            if (!q.hasMindMap) missing.add("مایندمپ")
                                            append(missing.joinToString("، "))
                                        },
                                        containerColor = tokens.wrong.copy(alpha = 0.18f),
                                        contentColor = tokens.wrong
                                    )
                                }
                            }
                        }
                        Text(
                            text = PersianTextUtils.formatDigits(cleanStem, settings.usePersianDigits),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4.3 Practice Screen (تمرین):
 * - Stem with rich clinical rendering (tables, lab value blocks, bold key phrases)
 * - Options with letter badges (الف ب ج د), immediate feedback mode vs reveal-at-end mode
 * - Option vote percentages + trap indicator
 * - Correct/wrong with explicit ✓ / ✗ glyphs
 * - Bookmark / Star / Flag toggles + Per-question personal note editor
 * - Lesson reader & High-res Mind-map buttons
 * - Structured Drug Safety Card (Pregnancy Category + GFR/CrCl renal dosing)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
    state: MedoUiState,
    questionsList: List<QuestionEntity>,
    currentQuestion: QuestionEntity,
    currentProgress: UserProgressEntity?,
    onSelectOption: (Int) -> Unit,
    onToggleImmediateMode: () -> Unit,
    onRevealAnswer: () -> Unit,
    onNavigateQuestion: (String) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleFlag: () -> Unit,
    onSaveNote: (String) -> Unit,
    onOpenLessonReader: () -> Unit,
    onOpenMindMap: () -> Unit,
    onOpenFilterList: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current
    val scrollState = rememberScrollState()

    val currentIndex = questionsList.indexOfFirst { it.siteQuestionId == currentQuestion.siteQuestionId }.coerceAtLeast(0)
    val options = remember(currentQuestion.optionsJson) {
        MedoPdfEngine.parseOptionsJson(currentQuestion.optionsJson)
    }
    val votes = remember(currentQuestion.optionVotesJson) {
        currentQuestion.optionVotesJson?.let { json ->
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getInt(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    val chosenOption = currentProgress?.lastSelectedOption
    val isAnswerRevealed = if (state.immediateFeedbackMode) {
        chosenOption != null
    } else {
        currentQuestion.siteQuestionId in state.revealedInEndModeIds
    }

    var noteDraft by remember(currentQuestion.siteQuestionId, currentProgress?.personalNote) {
        mutableStateOf(currentProgress?.personalNote.orEmpty())
    }
    var showJumpGrid by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("practice_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Control Strip: Question counter, Mode switch, Bookmark/Star/Flag
        GlassCard(
            cornerRadius = 20.dp,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MedoPill(
                            text = "سوال ${PersianTextUtils.formatNumber(currentIndex + 1, settings.usePersianDigits)} از ${PersianTextUtils.formatNumber(questionsList.size, settings.usePersianDigits)}",
                            onClick = { showJumpGrid = !showJumpGrid },
                            modifier = Modifier.testTag("practice_jump_grid_toggle")
                        )
                        MedoPill(
                            text = if (state.immediateFeedbackMode) "پاسخ فوری" else "نمایش در پایان",
                            containerColor = tokens.accent.copy(alpha = 0.18f),
                            contentColor = tokens.accent,
                            onClick = onToggleImmediateMode,
                            modifier = Modifier.testTag("toggle_feedback_mode_btn")
                        )
                        MedoPill(
                            text = "فیلتر (${state.activeQuestionFilter.labelFa})",
                            onClick = onOpenFilterList
                        )
                    }

                    Row {
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.testTag("bookmark_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (currentProgress?.bookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "نشان کردن سوال",
                                tint = if (currentProgress?.bookmarked == true) tokens.accent else tokens.textSecondary
                            )
                        }
                        IconButton(
                            onClick = onToggleStar,
                            modifier = Modifier.testTag("star_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (currentProgress?.starred == true) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "ستاره‌دار کردن سوال",
                                tint = if (currentProgress?.starred == true) tokens.warning else tokens.textSecondary
                            )
                        }
                        IconButton(
                            onClick = onToggleFlag,
                            modifier = Modifier.testTag("flag_toggle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "پرچم‌گذاری برای بازبینی",
                                tint = if (currentProgress?.flagged == true) tokens.wrong else tokens.textSecondary
                            )
                        }
                    }
                }

                // Content status badges (Section 5.6)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (currentQuestion.isTrap) {
                        MedoPill(
                            text = "⚠ سوال دام‌دار (اکثریت اشتباه زده‌اند)",
                            containerColor = tokens.warning.copy(alpha = 0.2f),
                            contentColor = tokens.warning
                        )
                    }
                    if (!currentQuestion.hasLesson) {
                        MedoPill(
                            text = "فاقد درسنامه",
                            containerColor = tokens.wrong.copy(alpha = 0.16f),
                            contentColor = tokens.wrong
                        )
                    }
                    if (!currentQuestion.hasMindMap) {
                        MedoPill(
                            text = "فاقد مایندمپ",
                            containerColor = tokens.wrong.copy(alpha = 0.16f),
                            contentColor = tokens.wrong
                        )
                    }
                }

                if (showJumpGrid) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        questionsList.forEachIndexed { idx, q ->
                            val isCur = q.siteQuestionId == currentQuestion.siteQuestionId
                            MedoPill(
                                text = PersianTextUtils.formatNumber(idx + 1, settings.usePersianDigits),
                                containerColor = if (isCur) tokens.accent else tokens.surfaceTint,
                                contentColor = if (isCur) tokens.accentOn else tokens.textPrimary,
                                onClick = {
                                    onNavigateQuestion(q.siteQuestionId)
                                    showJumpGrid = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Question Stem Reading Surface (Guaranteed >= 4.5:1 contrast)
        GlassCard(
            isReadingSurface = true,
            contentPadding = PaddingValues(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("question_stem_card")
        ) {
            RichClinicalHtmlView(html = currentQuestion.stemHtml)
        }

        // 3. Options with الف ب ج د badges, ✓ / ✗ glyphs, and vote percentages
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { optIdx, optText ->
                val visualState = when {
                    !isAnswerRevealed -> {
                        if (chosenOption == optIdx) OptionVisualState.SELECTED else OptionVisualState.NEUTRAL
                    }
                    optIdx == currentQuestion.correctOptionIndex -> OptionVisualState.CORRECT
                    chosenOption == optIdx && optIdx != currentQuestion.correctOptionIndex -> OptionVisualState.WRONG
                    else -> OptionVisualState.NEUTRAL
                }
                ClinicalOptionRow(
                    index = optIdx,
                    text = optText,
                    state = visualState,
                    votePercent = votes?.getOrNull(optIdx),
                    showVotes = isAnswerRevealed && votes != null,
                    onClick = { onSelectOption(optIdx) }
                )
            }
        }

        if (!state.immediateFeedbackMode && !isAnswerRevealed && chosenOption != null) {
            MedoPrimaryButton(
                text = "نمایش پاسخ صحیح و تشریح",
                onClick = onRevealAnswer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reveal_answer_btn")
            )
        }

        // 4. Revealed Explanation, Drug Safety Card, Lesson Reader & Mind-Map Actions
        if (isAnswerRevealed) {
            GlassCard(
                isReadingSurface = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explanation_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "پاسخ تشریحی و نکات کلیدی",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.accent,
                        fontWeight = FontWeight.Bold
                    )

                    val expHtml = currentQuestion.explanationHtml
                        ?: "<p>پاسخ تشریحی برای این سوال در بسته فعلی ثبت نشده است.</p>"
                    RichClinicalHtmlView(html = expHtml)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (currentQuestion.hasLesson) {
                            MedoPrimaryButton(
                                text = "مطالعه درسنامه کامل",
                                onClick = onOpenLessonReader,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_lesson_reader_btn")
                            )
                        }
                        if (currentQuestion.hasMindMap) {
                            MedoSecondaryButton(
                                text = "مشاهده مایندمپ",
                                onClick = onOpenMindMap,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_mindmap_btn")
                            )
                        }
                    }
                }
            }

            if (!currentQuestion.lessonHtml.isNullOrBlank()) {
                DeterministicSourceLessonCard(
                    questionId = currentQuestion.siteQuestionId,
                    lessonHtml = currentQuestion.lessonHtml
                )
            }
        }

        // 5. Per-Question Personal Note Editor
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "یادداشت بالینی شخصی برای این سوال:",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    placeholder = { Text("نکته شخصی، دوز دارو یا خلاصه خود را بنویسید...") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("personal_note_input")
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MedoPill(
                        text = "ذخیره یادداشت",
                        onClick = { onSaveNote(noteDraft) },
                        modifier = Modifier.testTag("save_note_btn")
                    )
                }
            }
        }

        // 6. Next / Previous Quick Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val prevId = questionsList.getOrNull(currentIndex - 1)?.siteQuestionId
            val nextId = questionsList.getOrNull(currentIndex + 1)?.siteQuestionId

            MedoSecondaryButton(
                text = "سوال قبلی",
                enabled = prevId != null,
                onClick = { if (prevId != null) onNavigateQuestion(prevId) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("prev_question_btn")
            )
            MedoPrimaryButton(
                text = "سوال بعدی",
                enabled = nextId != null,
                onClick = { if (nextId != null) onNavigateQuestion(nextId) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("next_question_btn")
            )
        }
    }
}
