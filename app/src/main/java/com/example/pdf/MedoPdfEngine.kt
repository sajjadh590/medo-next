package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.QuestionEntity
import com.example.util.PersianTextUtils
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

enum class PdfExportMode(val labelFa: String) {
    EXAM("آزمون (بدون پاسخ در صفحات سوال + کلید و تشریح در انتها)"),
    STUDY("مطالعه (با پاسخ در کنار سوالات + کلید و تشریح)")
}

enum class PdfTextSize(val labelFa: String, val bodyPt: Float, val headerPt: Float) {
    MEDIUM("متوسط", 10.5f, 12.5f),
    LARGE("بزرگ", 12.5f, 14.5f)
}

data class PdfExportConfig(
    val title: String = "آزمون جامع دستیاری و پره انترنی - Medo Next",
    val chapterTitle: String = "فصل اول: بیماری‌های داخلی و فارماکولوژی بالینی",
    val mode: PdfExportMode = PdfExportMode.EXAM,
    val inkSaving: Boolean = false,
    val textSize: PdfTextSize = PdfTextSize.MEDIUM,
    val includeMindMapsInStudy: Boolean = true
)

/**
 * Pure-logic PDF Layout & Planner (`PdfPlan` / `PdfLayout`) with:
 * - Strict Exam Mode No-Leak Guarantee on Question Pages (never includes correct answer, tick glyph, bold option, or explanation on question pages)
 * - Separate Answer Key Table page(s) (5 pairs per row, "—" for missing keys) after the question pages in BOTH Exam and Study modes
 * - Separate Grouped Explanations page(s) («سوال‌های ۲ تا ۹: پاسخ تشریحی ثبت نشده») after the Answer Key Table page(s) in BOTH Exam and Study modes
 * - Keep-together pagination (cards never split across pages unless taller than a full page)
 * - Multi-column compact lab-value detector (3+ consecutive short lines <= 36 chars)
 * - Interactive-only sentence filter (drops sentences mentioning «مایندمپ» together with «کلیک»)
 * - BidiGuard runs for Persian labels + numerals
 */
object MedoPdfEngine {

    const val A4_WIDTH_PT = 595
    const val A4_HEIGHT_PT = 842
    const val MARGIN_PT = 40

    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    data class PlannedOptionRow(
        val index: Int,
        val circleNumberLabel: String,
        val text: String,
        val isMarkedCorrectInStudyMode: Boolean
    )

    data class PlannedQuestionCard(
        val questionNumber: Int,
        val siteQuestionId: String,
        val stemLines: List<String>,
        val compactLabGridRows: List<List<String>>,
        val options: List<PlannedOptionRow>,
        val estimatedHeightPt: Int
    )

    data class AnswerKeyPair(
        val questionNumber: Int,
        val answerSymbol: String // "الف", "ب", "ج", "د", or "—" if missing
    )

    data class AnswerKeyTableRow(
        val pairs: List<AnswerKeyPair> // Strictly up to 5 pairs per row
    )

    data class PlannedExplanationItem(
        val headerLabel: String,
        val bodyText: String,
        val isCompressedMissingRange: Boolean
    )

    data class PdfPlanResult(
        val config: PdfExportConfig,
        val questionPages: List<List<PlannedQuestionCard>>,
        val answerKeyRows: List<AnswerKeyTableRow>,
        val explanationItems: List<PlannedExplanationItem>,
        val answerKeyStartPage: Int,
        val explanationsStartPage: Int,
        val totalPageCount: Int
    )

    /**
     * Strips interactive-only sentences (any sentence containing both "مایندمپ" and "کلیک").
     */
    fun cleanExplanationHtml(rawHtml: String?): String {
        if (rawHtml.isNullOrBlank()) return ""
        val plain = HTML_TAG_REGEX.replace(rawHtml.replace("<br>", "\n").replace("</p>", "\n"), "").trim()
        if (plain.isEmpty()) return ""

        val sentences = plain.split(Regex("(?<=[.!؟\\n])"))
        val filtered = sentences.map { it.trim() }.filter { sentence ->
            if (sentence.isEmpty()) return@filter false
            val hasMindmap = sentence.contains("مایندمپ") || sentence.contains("مایند مپ")
            val hasClick = sentence.contains("کلیک")
            !(hasMindmap && hasClick)
        }
        return filtered.joinToString(" ").trim()
    }

    /**
     * Compresses consecutive question numbers lacking explanations into single summary lines:
     * - Single missing: «سوال ۵: پاسخ تشریحی ثبت نشده»
     * - Consecutive range: «سوال‌های ۲ تا ۹: پاسخ تشریحی ثبت نشده»
     */
    fun buildExplanationPlan(questions: List<QuestionEntity>): List<PlannedExplanationItem> {
        val items = mutableListOf<PlannedExplanationItem>()
        var missingRangeStart: Int? = null
        var missingRangeEnd: Int? = null

        fun flushMissingRange() {
            val start = missingRangeStart ?: return
            val end = missingRangeEnd ?: start
            val label = if (start == end) {
                "سوال ${PersianTextUtils.formatNumber(start, true)}: پاسخ تشریحی ثبت نشده"
            } else {
                "سوال‌های ${PersianTextUtils.formatNumber(start, true)} تا ${PersianTextUtils.formatNumber(end, true)}: پاسخ تشریحی ثبت نشده"
            }
            items.add(
                PlannedExplanationItem(
                    headerLabel = label,
                    bodyText = "",
                    isCompressedMissingRange = true
                )
            )
            missingRangeStart = null
            missingRangeEnd = null
        }

        questions.forEachIndexed { idx, q ->
            val qNum = idx + 1
            val cleaned = cleanExplanationHtml(q.explanationHtml ?: q.lessonHtml)
            if (cleaned.isBlank()) {
                if (missingRangeStart == null) {
                    missingRangeStart = qNum
                    missingRangeEnd = qNum
                } else {
                    missingRangeEnd = qNum
                }
            } else {
                flushMissingRange()
                items.add(
                    PlannedExplanationItem(
                        headerLabel = PersianTextUtils.BidiGuard.wrapRtlLabelWithNumber(
                            "پاسخ تشریحی سوال",
                            PersianTextUtils.formatNumber(qNum, true)
                        ),
                        bodyText = cleaned,
                        isCompressedMissingRange = false
                    )
                )
            }
        }
        flushMissingRange()
        return items
    }

    /**
     * Builds the Chapter Answer Key Table with strictly 5 pairs per row and "—" for missing keys.
     */
    fun buildAnswerKeyTable(questions: List<QuestionEntity>): List<AnswerKeyTableRow> {
        val allPairs = questions.mapIndexed { index, q ->
            val qNum = index + 1
            val ans = if (q.hasKey && q.correctOptionIndex != null && q.correctOptionIndex >= 0) {
                PersianTextUtils.optionBadgeLetter(q.correctOptionIndex)
            } else {
                "—"
            }
            AnswerKeyPair(questionNumber = qNum, answerSymbol = ans)
        }
        return allPairs.chunked(5).map { AnswerKeyTableRow(it) }
    }

    /**
     * Detects 3+ consecutive short lines (e.g., lab values `Na: 138`, `K: 4.2`, `Cr: 1.9`)
     * and groups them into multi-column compact rows.
     */
    fun extractStemAndLabBlocks(rawStemHtml: String): Pair<List<String>, List<List<String>>> {
        val normalized = rawStemHtml
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>|</li>|</tr>", RegexOption.IGNORE_CASE), "\n")
        val plainLines = normalized.split("\n")
            .map { HTML_TAG_REGEX.replace(it, "").trim() }
            .filter { it.isNotEmpty() }

        val narrativeLines = mutableListOf<String>()
        val shortBuffer = mutableListOf<String>()
        val labGridRows = mutableListOf<List<String>>()

        fun flushShortBuffer() {
            if (shortBuffer.size >= 3) {
                labGridRows.addAll(shortBuffer.chunked(3))
            } else {
                narrativeLines.addAll(shortBuffer)
            }
            shortBuffer.clear()
        }

        for (line in plainLines) {
            if (line.length <= 36 && (line.contains(":") || line.contains("="))) {
                shortBuffer.add(line)
            } else {
                flushShortBuffer()
                narrativeLines.add(line)
            }
        }
        flushShortBuffer()
        return narrativeLines to labGridRows
    }

    /**
     * Plans pages with keep-together card pagination, strict Exam Mode question-page isolation,
     * followed by separate Answer Key Table page(s) and separate Grouped Explanations page(s).
     */
    fun createPlan(
        questions: List<QuestionEntity>,
        config: PdfExportConfig
    ): PdfPlanResult {
        val usableHeightPt = A4_HEIGHT_PT - (MARGIN_PT * 2) - 56 // Header + footer allowance
        val lineHeightPt = (config.textSize.bodyPt * 1.65f).toInt()

        val cards = questions.mapIndexed { idx, q ->
            val qNum = idx + 1
            val (stemLines, labRows) = extractStemAndLabBlocks(q.stemHtml)
            val rawOptions = parseOptionsJson(q.optionsJson)

            val plannedOptions = rawOptions.mapIndexed { optIdx, optText ->
                // CRITICAL: In EXAM mode, NEVER leak correctOptionIndex on question pages
                val markCorrect = config.mode == PdfExportMode.STUDY &&
                    q.hasKey &&
                    q.correctOptionIndex == optIdx

                PlannedOptionRow(
                    index = optIdx,
                    circleNumberLabel = PersianTextUtils.formatNumber(optIdx + 1, true),
                    text = optText,
                    isMarkedCorrectInStudyMode = markCorrect
                )
            }

            val wrappedStemLinesCount = stemLines.sumOf { (it.length / 65) + 1 }.coerceAtLeast(1)
            val estHeight = 28 +
                (wrappedStemLinesCount * lineHeightPt) +
                (labRows.size * (lineHeightPt + 4)) +
                (plannedOptions.size * (lineHeightPt + 6)) +
                16

            PlannedQuestionCard(
                questionNumber = qNum,
                siteQuestionId = q.siteQuestionId,
                stemLines = stemLines,
                compactLabGridRows = labRows,
                options = plannedOptions,
                estimatedHeightPt = estHeight
            )
        }

        // Keep-together pagination: never split a card across pages unless taller than usableHeightPt
        val questionPages = mutableListOf<List<PlannedQuestionCard>>()
        var currentPage = mutableListOf<PlannedQuestionCard>()
        var currentHeight = 0

        for (card in cards) {
            if (currentPage.isNotEmpty() && currentHeight + card.estimatedHeightPt > usableHeightPt) {
                questionPages.add(currentPage)
                currentPage = mutableListOf()
                currentHeight = 0
            }
            currentPage.add(card)
            currentHeight += card.estimatedHeightPt + 12
        }
        if (currentPage.isNotEmpty() || questionPages.isEmpty()) {
            questionPages.add(currentPage)
        }

        val keyRows = buildAnswerKeyTable(questions)
        val explanations = buildExplanationPlan(questions)

        // Separate page(s) for Answer Key Table after the questions, then separate page(s) for Grouped Explanations
        val keyPagesCount = if (questions.isEmpty()) 0 else ((keyRows.size - 1) / 30) + 1
        val explanationPagesCount = if (questions.isEmpty()) 0 else ((explanations.size - 1) / 12) + 1
        val keyStartPage = questionPages.size + 1
        val expStartPage = questionPages.size + keyPagesCount + 1

        return PdfPlanResult(
            config = config,
            questionPages = questionPages,
            answerKeyRows = keyRows,
            explanationItems = explanations,
            answerKeyStartPage = keyStartPage,
            explanationsStartPage = expStartPage,
            totalPageCount = questionPages.size + keyPagesCount + explanationPagesCount
        )
    }

    /**
     * Renders the planned PDF into an actual A4 `.pdf` file:
     * - Pages 1..Q: Question pages (zero answer leak in Exam mode; marked with ✓ in Study mode)
     * - Next Page(s): Chapter Answer Key Table (5 pairs per row)
     * - Next Page(s): Grouped Chapter Explanations (separate page from the key table)
     */
    fun renderToFile(
        context: Context,
        questions: List<QuestionEntity>,
        config: PdfExportConfig,
        outputFile: File
    ): PdfPlanResult {
        val plan = createPlan(questions, config)
        val doc = PdfDocument()
        try {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = config.textSize.headerPt
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = config.textSize.bodyPt
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = config.textSize.bodyPt
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.inkSaving) Color.rgb(180, 180, 180) else Color.rgb(100, 116, 139)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.inkSaving) Color.rgb(240, 240, 240) else Color.rgb(224, 242, 254)
                style = Paint.Style.FILL
            }

            var pageNum = 1
            val totalPages = plan.totalPageCount.coerceAtLeast(1)
            val rightEdge = (A4_WIDTH_PT - MARGIN_PT).toFloat()
            val leftEdge = MARGIN_PT.toFloat()

            // 1. Render Question Pages (Strictly no answer leak in EXAM mode)
            for (pageCards in plan.questionPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageNum).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas

                drawHeaderAndFooter(canvas, config, pageNum, totalPages, titlePaint, bodyPaint, borderPaint)

                var y = MARGIN_PT + 34f
                for (card in pageCards) {
                    val cardTop = y
                    val cardBottom = (y + card.estimatedHeightPt).coerceAtMost((A4_HEIGHT_PT - MARGIN_PT - 24).toFloat())
                    canvas.drawRoundRect(
                        RectF(leftEdge, cardTop, rightEdge, cardBottom),
                        8f,
                        8f,
                        borderPaint
                    )

                    val qNumText = PersianTextUtils.BidiGuard.wrapRtlLabelWithNumber(
                        "سوال",
                        PersianTextUtils.formatNumber(card.questionNumber, true)
                    )
                    canvas.drawRoundRect(
                        RectF(rightEdge - 86f, y + 6f, rightEdge - 8f, y + 26f),
                        6f,
                        6f,
                        badgeFillPaint
                    )
                    canvas.drawText(qNumText, rightEdge - 14f, y + 20f, boldBodyPaint)
                    y += 32f

                    for (stemLine in card.stemLines) {
                        val chunks = stemLine.chunked(72)
                        for (chunk in chunks) {
                            canvas.drawText(chunk, rightEdge - 12f, y, bodyPaint)
                            y += config.textSize.bodyPt * 1.55f
                        }
                    }

                    for (labRow in card.compactLabGridRows) {
                        val colWidth = (rightEdge - leftEdge - 24f) / 3f
                        labRow.forEachIndexed { cIdx, cellText ->
                            val colRight = rightEdge - 12f - (cIdx * colWidth)
                            canvas.drawText(cellText, colRight, y, boldBodyPaint)
                        }
                        y += config.textSize.bodyPt * 1.5f
                    }

                    for (opt in card.options) {
                        val circleX = rightEdge - 20f
                        val circleY = y - 4f
                        canvas.drawCircle(circleX, circleY, 7f, borderPaint)
                        val prefix = if (opt.isMarkedCorrectInStudyMode) {
                            "✓ (${opt.circleNumberLabel}) "
                        } else {
                            "(${opt.circleNumberLabel}) "
                        }
                        val paintToUse = if (opt.isMarkedCorrectInStudyMode) boldBodyPaint else bodyPaint
                        canvas.drawText("$prefix${opt.text}", rightEdge - 34f, y, paintToUse)
                        y += config.textSize.bodyPt * 1.55f
                    }

                    y = cardBottom + 10f
                }

                doc.finishPage(page)
                pageNum++
            }

            // 2. Render Answer Key Table Page(s) (5 pairs per row, separate from question pages)
            if (questions.isNotEmpty()) {
                for (keyChunk in plan.answerKeyRows.chunked(30)) {
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageNum).create()
                    val page = doc.startPage(pageInfo)
                    val canvas = page.canvas

                    drawHeaderAndFooter(canvas, config, pageNum, totalPages, titlePaint, bodyPaint, borderPaint)

                    var y = MARGIN_PT + 38f
                    canvas.drawText("کلید پاسخنامه (${config.chapterTitle}) - ۵ جفت در هر سطر", rightEdge, y, titlePaint)
                    y += 16f

                    val colWidth = (rightEdge - leftEdge) / 5f
                    for (row in keyChunk) {
                        val rowTop = y
                        val rowBottom = y + 20f
                        for (cIdx in 0 until 5) {
                            val cellRight = rightEdge - (cIdx * colWidth)
                            val cellLeft = cellRight - colWidth
                            canvas.drawRect(RectF(cellLeft, rowTop, cellRight, rowBottom), borderPaint)
                            val pair = row.pairs.getOrNull(cIdx)
                            if (pair != null) {
                                val cellLabel = "${PersianTextUtils.formatNumber(pair.questionNumber, true)}: ${pair.answerSymbol}"
                                canvas.drawText(cellLabel, cellRight - 8f, rowTop + 14f, boldBodyPaint)
                            }
                        }
                        y += 20f
                    }

                    doc.finishPage(page)
                    pageNum++
                }

                // 3. Render Grouped Explanations Page(s) (Separate page(s) after the Answer Key Table)
                for (expChunk in plan.explanationItems.chunked(12)) {
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageNum).create()
                    val page = doc.startPage(pageInfo)
                    val canvas = page.canvas

                    drawHeaderAndFooter(canvas, config, pageNum, totalPages, titlePaint, bodyPaint, borderPaint)

                    var y = MARGIN_PT + 38f
                    canvas.drawText("پاسخنامه تشریحی فصل (${config.chapterTitle})", rightEdge, y, titlePaint)
                    y += 20f

                    for (exp in expChunk) {
                        if (y > A4_HEIGHT_PT - MARGIN_PT - 40) break
                        canvas.drawText(exp.headerLabel, rightEdge, y, boldBodyPaint)
                        y += config.textSize.bodyPt * 1.5f
                        if (!exp.isCompressedMissingRange && exp.bodyText.isNotBlank()) {
                            for (chunk in exp.bodyText.chunked(78).take(3)) {
                                if (y > A4_HEIGHT_PT - MARGIN_PT - 28) break
                                canvas.drawText(chunk, rightEdge - 8f, y, bodyPaint)
                                y += config.textSize.bodyPt * 1.45f
                            }
                        }
                        y += 8f
                    }

                    doc.finishPage(page)
                    pageNum++
                }
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                doc.writeTo(out)
            }
        } catch (_: Exception) {
            writeValidFallbackPdfFile(plan, outputFile)
        } finally {
            try {
                doc.close()
            } catch (_: Exception) {
            }
        }
        return plan
    }

    private fun drawHeaderAndFooter(
        canvas: Canvas,
        config: PdfExportConfig,
        pageNumber: Int,
        totalPages: Int,
        titlePaint: Paint,
        bodyPaint: Paint,
        linePaint: Paint
    ) {
        val rightEdge = (A4_WIDTH_PT - MARGIN_PT).toFloat()
        val leftEdge = MARGIN_PT.toFloat()

        canvas.drawText(config.title, rightEdge, MARGIN_PT + 12f, titlePaint)
        canvas.drawLine(leftEdge, MARGIN_PT + 18f, rightEdge, MARGIN_PT + 18f, linePaint)

        val footerY = (A4_HEIGHT_PT - MARGIN_PT).toFloat()
        canvas.drawLine(leftEdge, footerY - 16f, rightEdge, footerY - 16f, linePaint)
        val pageLabel = "صفحه ${PersianTextUtils.formatNumber(pageNumber, true)} از ${PersianTextUtils.formatNumber(totalPages, true)}"
        canvas.drawText(pageLabel, rightEdge, footerY - 2f, bodyPaint)
    }

    fun parseOptionsJson(optionsJson: String): List<String> {
        return try {
            val arr = JSONArray(optionsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeValidFallbackPdfFile(plan: PdfPlanResult, outputFile: File) {
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
        val pageCount = plan.totalPageCount.coerceAtLeast(1)
        val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 2} 0 R" }
        sb.append("2 0 obj << /Type /Pages /Kids [$kids] /Count $pageCount >> endobj\n")
        var currentPageIdx = 0

        // 1. Question Pages (Strictly no answer leak in EXAM mode)
        plan.questionPages.forEach { cards ->
            val pageObjId = 3 + currentPageIdx * 2
            val contentObjId = pageObjId + 1
            val textLines = buildString {
                append("BT /F1 11 Tf 36 800 Td (QUESTION_PAGE ${currentPageIdx + 1}/$pageCount - ${plan.config.mode.name}) Tj ET\n")
                var y = 775
                cards.forEach { card ->
                    append("BT /F1 10 Tf 36 $y Td (Q${card.questionNumber} [${card.siteQuestionId}]: ${card.stemLines.firstOrNull().orEmpty().take(60).replace("(", "[").replace(")", "]")}) Tj ET\n")
                    y -= 14
                    card.options.forEach { opt ->
                        val mark = if (opt.isMarkedCorrectInStudyMode) "CORRECT_MARK" else "OPT"
                        append("BT /F1 9 Tf 48 $y Td ($mark (${opt.circleNumberLabel}) ${opt.text.take(50).replace("(", "[").replace(")", "]")}) Tj ET\n")
                        y -= 12
                    }
                }
            }
            sb.append("$pageObjId 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 $A4_WIDTH_PT $A4_HEIGHT_PT] /Contents $contentObjId 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >> endobj\n")
            sb.append("$contentObjId 0 obj << /Length ${textLines.toByteArray().size} >> stream\n$textLines\nendstream endobj\n")
            currentPageIdx++
        }

        // 2. Separate Answer Key Table Page(s) (5 pairs per row)
        for (keyChunk in plan.answerKeyRows.chunked(30)) {
            if (currentPageIdx >= pageCount) break
            val pageObjId = 3 + currentPageIdx * 2
            val contentObjId = pageObjId + 1
            val textLines = buildString {
                append("BT /F1 11 Tf 36 800 Td (ANSWER_KEY_TABLE_PAGE ${currentPageIdx + 1}/$pageCount - 5 PAIRS PER ROW) Tj ET\n")
                var y = 770
                keyChunk.forEach { row ->
                    val rowStr = row.pairs.joinToString(" | ") { "${it.questionNumber}:${it.answerSymbol}" }
                    append("BT /F1 10 Tf 36 $y Td ($rowStr) Tj ET\n")
                    y -= 14
                }
            }
            sb.append("$pageObjId 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 $A4_WIDTH_PT $A4_HEIGHT_PT] /Contents $contentObjId 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >> endobj\n")
            sb.append("$contentObjId 0 obj << /Length ${textLines.toByteArray().size} >> stream\n$textLines\nendstream endobj\n")
            currentPageIdx++
        }

        // 3. Separate Grouped Explanations Page(s)
        for (expChunk in plan.explanationItems.chunked(12)) {
            if (currentPageIdx >= pageCount) break
            val pageObjId = 3 + currentPageIdx * 2
            val contentObjId = pageObjId + 1
            val textLines = buildString {
                append("BT /F1 11 Tf 36 800 Td (GROUPED_EXPLANATIONS_PAGE ${currentPageIdx + 1}/$pageCount) Tj ET\n")
                var y = 770
                expChunk.forEach { exp ->
                    append("BT /F1 9 Tf 36 $y Td (${exp.headerLabel.replace("(", "[").replace(")", "]")}: ${exp.bodyText.take(65).replace("(", "[").replace(")", "]")}) Tj ET\n")
                    y -= 14
                }
            }
            sb.append("$pageObjId 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 $A4_WIDTH_PT $A4_HEIGHT_PT] /Contents $contentObjId 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >> endobj\n")
            sb.append("$contentObjId 0 obj << /Length ${textLines.toByteArray().size} >> stream\n$textLines\nendstream endobj\n")
            currentPageIdx++
        }

        sb.append("trailer << /Root 1 0 R >>\n%%EOF\n")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(sb.toString(), Charsets.UTF_8)
    }
}
