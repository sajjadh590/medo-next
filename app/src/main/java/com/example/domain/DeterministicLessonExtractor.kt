package com.example.domain

/**
 * Deterministic Lesson Fact Extractor (Section B — Medical Data Safety):
 * - Extracts clinical adjustment/category rows ONLY when explicitly present in the imported question's
 *   own `lessonHtml` (`<table>` rows or explicit `CrCl` / `GFR` / `رده بارداری` sentences).
 * - Never invents, guesses, or stores model-authored medical facts.
 * - Always records the verbatim source snippet from the imported lesson text.
 */
object DeterministicLessonExtractor {

    data class ExtractedLessonRow(
        val parameterLabel: String,
        val valueText: String,
        val verbatimSourceSnippet: String
    )

    data class DeterministicLessonSummary(
        val sourceLessonTitle: String,
        val extractedRows: List<ExtractedLessonRow>
    )

    private val TR_REGEX = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TD_REGEX = Regex("<t[hd][^>]*>(.*?)</t[hd]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG_STRIP = Regex("<[^>]+>")

    fun extractFromLessonHtml(
        questionId: String,
        lessonHtml: String?
    ): DeterministicLessonSummary? {
        if (lessonHtml.isNullOrBlank()) return null

        val rows = mutableListOf<ExtractedLessonRow>()

        // 1. Deterministically extract rows from HTML tables that mention GFR, CrCl, or بارداری
        val tableMatches = TR_REGEX.findAll(lessonHtml).toList()
        for (tr in tableMatches) {
            val cells = TD_REGEX.findAll(tr.groupValues[1])
                .map { TAG_STRIP.replace(it.groupValues[1], "").trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (cells.size >= 2) {
                val joined = cells.joinToString(" | ")
                val hasClinicalMarker = joined.contains("CrCl", ignoreCase = true) ||
                    joined.contains("GFR", ignoreCase = true) ||
                    joined.contains("بارداری") ||
                    joined.contains("دیالیز")
                if (hasClinicalMarker) {
                    rows.add(
                        ExtractedLessonRow(
                            parameterLabel = cells.first(),
                            valueText = cells.drop(1).joinToString(" — "),
                            verbatimSourceSnippet = "جدول درسنامه سوال $questionId: «$joined»"
                        )
                    )
                }
            }
        }

        // 2. If no table rows matched, check plain sentences for explicit CrCl / GFR / رده بارداری mentions
        if (rows.isEmpty()) {
            val plainSentences = TAG_STRIP.replace(lessonHtml, " ")
                .split(Regex("(?<=[.!؟\\n])"))
                .map { it.trim() }
                .filter { it.length in 12..260 }

            for (sentence in plainSentences) {
                if (sentence.contains("CrCl", ignoreCase = true) ||
                    sentence.contains("GFR", ignoreCase = true) ||
                    sentence.contains("رده بارداری")
                ) {
                    rows.add(
                        ExtractedLessonRow(
                            parameterLabel = "نکته مستخرج از متن درسنامه",
                            valueText = sentence,
                            verbatimSourceSnippet = "متن درسنامه سوال $questionId"
                        )
                    )
                }
            }
        }

        if (rows.isEmpty()) return null
        return DeterministicLessonSummary(
            sourceLessonTitle = "استخراج قطعی از متن درسنامه واردشده (کد سوال: $questionId)",
            extractedRows = rows
        )
    }
}
