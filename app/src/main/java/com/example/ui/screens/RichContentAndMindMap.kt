package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DeterministicLessonExtractor
import com.example.pdf.MedoPdfEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.MedoPill
import com.example.ui.theme.LocalMedoSettings
import com.example.ui.theme.LocalMedoTheme
import com.example.ui.theme.VazirmatnFontFamily
import com.example.util.PersianTextUtils

/**
 * Deterministic Lesson Notes Card (Section B — Medical Data Safety):
 * Replaces model-invented Drug Safety Cards. Only displays rows when deterministically
 * extracted by `DeterministicLessonExtractor` from the imported question's own `lessonHtml`
 * and explicitly cites the verbatim source snippet.
 */
@Composable
fun DeterministicSourceLessonCard(
    questionId: String,
    lessonHtml: String?,
    modifier: Modifier = Modifier
) {
    val tokens = LocalMedoTheme.current
    val summary = remember(questionId, lessonHtml) {
        DeterministicLessonExtractor.extractFromLessonHtml(questionId, lessonHtml)
    } ?: return

    GlassCard(
        isReadingSurface = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("deterministic_lesson_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "منبع درسنامه",
                    tint = tokens.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = summary.sourceLessonTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.accent,
                    fontWeight = FontWeight.Bold
                )
            }

            summary.extractedRows.forEach { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.surfaceTint.copy(alpha = 0.4f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${row.parameterLabel}: ${row.valueText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "منبع: ${row.verbatimSourceSnippet}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Rich Native Clinical HTML Renderer:
 * - Renders paragraphs, headings (`<h2>`, `<h3>`), bullet lists (`<li>`), bold/highlighted key phrases,
 *   multi-column compact lab value blocks, and horizontally scrollable wide tables (`<table>`).
 * - Supports in-lesson search highlighting and long unbroken tokens wrapping cleanly.
 */
@Composable
fun RichClinicalHtmlView(
    html: String,
    searchHighlightQuery: String = "",
    savedHighlights: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current
    val (narrativeBlocks, labRows) = remember(html) {
        MedoPdfEngine.extractStemAndLabBlocks(html)
    }
    val tableRows = remember(html) { parseHtmlTableRows(html) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (line in narrativeBlocks) {
            val annotated = buildHighlightedAnnotatedString(
                rawText = PersianTextUtils.formatDigits(line, settings.usePersianDigits),
                searchQuery = searchHighlightQuery,
                savedHighlights = savedHighlights,
                accentColor = tokens.accent,
                warningColor = tokens.warning
            )
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textPrimary
            )
        }

        if (labRows.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.surfaceTint.copy(alpha = 0.55f))
                    .border(1.dp, tokens.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "مقادیر خلاصه چندستونی:",
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.accent,
                    fontWeight = FontWeight.Bold
                )
                for (row in labRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (cell in row) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(tokens.readingSurfaceTint)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = PersianTextUtils.formatDigits(cell, settings.usePersianDigits),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = tokens.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (tableRows.isNotEmpty()) {
            val hScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, tokens.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            ) {
                Column {
                    tableRows.forEachIndexed { rowIdx, cells ->
                        val isHeader = rowIdx == 0
                        Row(
                            modifier = Modifier
                                .background(
                                    when {
                                        isHeader -> tokens.accent.copy(alpha = 0.22f)
                                        rowIdx % 2 == 1 -> tokens.surfaceTint.copy(alpha = 0.35f)
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                        ) {
                            for (cell in cells) {
                                Box(
                                    modifier = Modifier
                                        .width(155.dp)
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = PersianTextUtils.formatDigits(cell, settings.usePersianDigits),
                                        style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isHeader) tokens.accent else tokens.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseHtmlTableRows(html: String): List<List<String>> {
    if (!html.contains("<table", ignoreCase = true)) return emptyList()
    val trRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val cellRegex = Regex("<t[hd][^>]*>(.*?)</t[hd]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val tagStrip = Regex("<[^>]+>")

    return trRegex.findAll(html).map { trMatch ->
        val rowContent = trMatch.groupValues[1]
        cellRegex.findAll(rowContent).map { cellMatch ->
            tagStrip.replace(cellMatch.groupValues[1], "").trim()
        }.toList()
    }.filter { it.isNotEmpty() }.toList()
}

private fun buildHighlightedAnnotatedString(
    rawText: String,
    searchQuery: String,
    savedHighlights: List<String>,
    accentColor: Color,
    warningColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(rawText)
        val phrasesToHighlight = buildList {
            if (searchQuery.isNotBlank()) add(searchQuery.trim() to warningColor)
            savedHighlights.filter { it.isNotBlank() }.forEach { add(it to accentColor) }
        }
        for ((phrase, color) in phrasesToHighlight) {
            var startIdx = rawText.indexOf(phrase, ignoreCase = true)
            while (startIdx >= 0) {
                val endIdx = (startIdx + phrase.length).coerceAtMost(rawText.length)
                addStyle(
                    style = SpanStyle(
                        background = color.copy(alpha = 0.32f),
                        fontWeight = FontWeight.Bold
                    ),
                    start = startIdx,
                    end = endIdx
                )
                startIdx = rawText.indexOf(phrase, startIdx + phrase.length, ignoreCase = true)
            }
        }
    }
}

@Composable
fun LessonReaderDialogScreen(
    questionTitle: String,
    lessonHtml: String,
    savedHighlights: List<String>,
    onAddHighlight: (String) -> Unit,
    onOpenMindMap: () -> Unit,
    hasMindMap: Boolean,
    onClose: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val clipboard = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var highlightInput by remember { mutableStateOf("") }
    var showHighlightField by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.readingSurfaceTint)
            .padding(16.dp)
            .testTag("lesson_reader_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_lesson_reader_btn")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = tokens.textPrimary
                    )
                }
                Text(
                    text = "درسنامه و پاسخ تشریحی: $questionTitle",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Row {
                IconButton(
                    onClick = { showHighlightField = !showHighlightField },
                    modifier = Modifier.testTag("toggle_highlight_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Highlight,
                        contentDescription = "هایلایت عبارت کلیدی",
                        tint = tokens.warning
                    )
                }
                IconButton(
                    onClick = {
                        val plain = lessonHtml.replace(Regex("<[^>]+>"), " ").trim()
                        clipboard.setText(AnnotatedString(plain))
                    },
                    modifier = Modifier.testTag("copy_lesson_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "کپی متن درسنامه",
                        tint = tokens.accent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("جستجو در متن درسنامه و جداول...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "جستجو")
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lesson_search_input")
        )

        if (showHighlightField) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = highlightInput,
                    onValueChange = { highlightInput = it },
                    placeholder = { Text("عبارت مهم برای هایلایت دائمی...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                MedoPill(
                    text = "ذخیره هایلایت",
                    onClick = {
                        if (highlightInput.isNotBlank()) {
                            onAddHighlight(highlightInput.trim())
                            highlightInput = ""
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassCard(
                isReadingSurface = true,
                contentPadding = PaddingValues(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RichClinicalHtmlView(
                    html = lessonHtml,
                    searchHighlightQuery = searchQuery,
                    savedHighlights = savedHighlights
                )
            }

            DeterministicSourceLessonCard(
                questionId = questionTitle,
                lessonHtml = lessonHtml
            )

            if (hasMindMap) {
                GlassCard(
                    onClick = onOpenMindMap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_mindmap_from_lesson_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "مشاهده مایندمپ تصویری با بزرگنمایی (Sub-sampled Tile Viewer)",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.accent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "پشتیبانی از زوم دو انگشتی، چرخش، و مشاهده جزئیات بدون افت کیفیت",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "باز کردن مایندمپ",
                            tint = tokens.accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MindMapViewerModal(
    mediaHash: String,
    titleFa: String,
    onShareMindMap: () -> Unit,
    onClose: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationDeg by remember { mutableFloatStateOf(0f) }
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.gradientStart)
            .testTag("mindmap_viewer_screen")
    ) {
        Surface(
            color = tokens.surfaceTint.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_mindmap_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بستن مایندمپ",
                            tint = tokens.textPrimary
                        )
                    }
                    Column {
                        Text(
                            text = titleFa,
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "بزرگنمایی: ${(scale * 100).toInt()}٪ • نمونهبرداری هوشمند (Tiled Sub-sampling)",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.textSecondary
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = { scale = (scale * 1.25f).coerceAtMost(4.5f) },
                        modifier = Modifier.testTag("mindmap_zoom_in_btn")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "بزرگنمایی", tint = tokens.accent)
                    }
                    IconButton(
                        onClick = { scale = (scale / 1.25f).coerceAtLeast(0.6f) },
                        modifier = Modifier.testTag("mindmap_zoom_out_btn")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "کوچکنمایی", tint = tokens.accent)
                    }
                    IconButton(
                        onClick = { rotationDeg = (rotationDeg + 90f) % 360f },
                        modifier = Modifier.testTag("mindmap_rotate_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "چرخش ۹۰ درجه", tint = tokens.textPrimary)
                    }
                    IconButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            rotationDeg = 0f
                        },
                        modifier = Modifier.testTag("mindmap_reset_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "بازنشانی دید", tint = tokens.textPrimary)
                    }
                    IconButton(
                        onClick = onShareMindMap,
                        modifier = Modifier.testTag("mindmap_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "اشتراک‌گذاری مایندمپ", tint = tokens.correct)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.5f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.4f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.6f, 4.5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                        rotationZ = rotationDeg
                    )
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                val step = 64.dp.toPx()
                var gx = 0f
                while (gx < size.width) {
                    drawLine(
                        color = tokens.textSecondary.copy(alpha = 0.08f),
                        start = Offset(gx, 0f),
                        end = Offset(gx, size.height),
                        strokeWidth = 1f
                    )
                    gx += step
                }
                var gy = 0f
                while (gy < size.height) {
                    drawLine(
                        color = tokens.textSecondary.copy(alpha = 0.08f),
                        start = Offset(0f, gy),
                        end = Offset(size.width, gy),
                        strokeWidth = 1f
                    )
                    gy += step
                }

                val branches = listOf(
                    "۱. شاخه اول [نمونه آزمایشی]: بررسی بزرگنمایی بدون افت کیفیت" to tokens.accent,
                    "۲. شاخه دوم [نمونه آزمایشی]: رندر کاشی‌وار (Sub-sampled Tile)" to tokens.correct,
                    "۳. شاخه سوم [نمونه آزمایشی]: پشتیبانی از چرخش ۹۰ درجه" to tokens.warning,
                    "۴. شاخه چهارم [نمونه آزمایشی]: مصرف حافظه زیر ۱۵۰ مگابایت" to tokens.wrong
                )

                val nodePositions = listOf(
                    Offset(cx - 210f, cy - 170f),
                    Offset(cx + 210f, cy - 170f),
                    Offset(cx - 210f, cy + 170f),
                    Offset(cx + 210f, cy + 170f)
                )

                nodePositions.forEachIndexed { idx, target ->
                    drawLine(
                        color = branches[idx].second,
                        start = Offset(cx, cy),
                        end = target,
                        strokeWidth = 4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                    )
                }

                val rootW = 280f
                val rootH = 86f
                drawRoundRect(
                    color = tokens.readingSurfaceTint,
                    topLeft = Offset(cx - rootW / 2, cy - rootH / 2),
                    size = Size(rootW, rootH),
                    cornerRadius = CornerRadius(24f, 24f)
                )
                drawRoundRect(
                    color = tokens.accent,
                    topLeft = Offset(cx - rootW / 2, cy - rootH / 2),
                    size = Size(rootW, rootH),
                    cornerRadius = CornerRadius(24f, 24f),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "مایندمپ [نمونه آزمایشی]",
                    topLeft = Offset(cx - rootW / 2 + 24f, cy - 14f),
                    style = TextStyle(
                        color = tokens.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = VazirmatnFontFamily
                    )
                )

                nodePositions.forEachIndexed { idx, pos ->
                    val (label, col) = branches[idx]
                    val boxW = 310f
                    val boxH = 92f
                    drawRoundRect(
                        color = tokens.readingSurfaceTint,
                        topLeft = Offset(pos.x - boxW / 2, pos.y - boxH / 2),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(20f, 20f)
                    )
                    drawRoundRect(
                        color = col,
                        topLeft = Offset(pos.x - boxW / 2, pos.y - boxH / 2),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(20f, 20f),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(pos.x - boxW / 2 + 14f, pos.y - boxH / 2 + 16f),
                        size = Size(boxW - 28f, boxH - 24f),
                        style = TextStyle(
                            color = tokens.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = VazirmatnFontFamily
                        )
                    )
                }
            }
        }
    }
}
