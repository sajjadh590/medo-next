package com.example.domain

import com.example.data.CategoryNodeEntity
import com.example.data.ClozeCardEntity
import com.example.data.QuestionEntity
import com.example.data.UserProgressEntity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * SM-2 Spaced Repetition Scheduler.
 * Ratings:
 * 0 = Again (دوباره - فراموش شده)
 * 1 = Hard (سخت)
 * 2 = Good (خوب)
 * 3 = Easy (آسان)
 */
object Sm2Scheduler {

    enum class Rating(val code: Int, val labelFa: String, val quality: Int) {
        AGAIN(0, "دوباره", 1),
        HARD(1, "سخت", 3),
        GOOD(2, "خوب", 4),
        EASY(3, "آسان", 5)
    }

    data class Sm2State(
        val repetitions: Int,
        val easinessFactor: Float,
        val intervalDays: Int,
        val dueEpochDay: Long
    )

    fun schedule(
        currentRepetitions: Int,
        currentEf: Float,
        currentIntervalDays: Int,
        rating: Rating,
        todayEpochDay: Long
    ): Sm2State {
        val q = rating.quality
        val newEf = max(
            1.3f,
            currentEf + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
        )

        if (rating == Rating.AGAIN) {
            return Sm2State(
                repetitions = 0,
                easinessFactor = newEf,
                intervalDays = 1,
                dueEpochDay = todayEpochDay + 1L
            )
        }

        val newReps = currentRepetitions + 1
        val newInterval = when (newReps) {
            1 -> if (rating == Rating.EASY) 4 else 1
            2 -> if (rating == Rating.HARD) 3 else 6
            else -> {
                val multiplier = when (rating) {
                    Rating.HARD -> 1.2f
                    Rating.GOOD -> newEf
                    Rating.EASY -> newEf * 1.3f
                    Rating.AGAIN -> 1.0f
                }
                max(currentIntervalDays + 1, (currentIntervalDays * multiplier).roundToInt())
            }
        }

        return Sm2State(
            repetitions = newReps,
            easinessFactor = newEf,
            intervalDays = newInterval,
            dueEpochDay = todayEpochDay + newInterval.toLong()
        )
    }
}

/**
 * Exam Scorer with configurable negative marking (default: 1/3 negative marking,
 * where 3 wrong answers cancel 1 correct answer; unanswered = 0).
 */
object ExamScorer {

    data class ScoreBreakdown(
        val totalQuestions: Int,
        val correctCount: Int,
        val wrongCount: Int,
        val unansweredCount: Int,
        val rawPercentNoNegative: Float,
        val netScoreOutOfTotal: Float,
        val netPercentWithNegative: Float
    )

    fun calculateScore(
        totalQuestions: Int,
        correctCount: Int,
        wrongCount: Int,
        negativeMarkingDivisor: Int = 3
    ): ScoreBreakdown {
        if (totalQuestions <= 0) {
            return ScoreBreakdown(0, 0, 0, 0, 0f, 0f, 0f)
        }
        val unanswered = (totalQuestions - correctCount - wrongCount).coerceAtLeast(0)
        val rawPct = (correctCount.toFloat() / totalQuestions.toFloat()) * 100f
        val penalty = if (negativeMarkingDivisor > 0) {
            wrongCount.toFloat() / negativeMarkingDivisor.toFloat()
        } else {
            0f
        }
        val netScore = correctCount.toFloat() - penalty
        val netPct = ((netScore / totalQuestions.toFloat()) * 100f).coerceIn(-100f, 100f)

        return ScoreBreakdown(
            totalQuestions = totalQuestions,
            correctCount = correctCount,
            wrongCount = wrongCount,
            unansweredCount = unanswered,
            rawPercentNoNegative = rawPct,
            netScoreOutOfTotal = netScore,
            netPercentWithNegative = netPct
        )
    }
}

/**
 * Runtime Cloze Flashcard Generator from lesson HTML (`<b>`, `<strong>`, `<mark>`, or `<span class="key">`).
 */
object ClozeGenerator {

    private val KEY_PHRASE_REGEX = Regex(
        "<(b|strong|mark)>(.*?)</\\1>|<span[^>]*style=[\"'][^\"']*color[^\"']*[\"'][^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    fun extractClozeCardsFromLesson(
        siteQuestionId: String,
        lessonHtml: String?,
        categoryTitle: String,
        todayEpochDay: Long
    ): List<ClozeCardEntity> {
        if (lessonHtml.isNullOrBlank()) return emptyList()
        val results = mutableListOf<ClozeCardEntity>()

        // Split lesson into sentence/paragraph blocks
        val paragraphs = lessonHtml.split(Regex("</p>|<br\\s*/?>|\\n"))
        var cardIndex = 0

        for (rawPara in paragraphs) {
            val matches = KEY_PHRASE_REGEX.findAll(rawPara).toList()
            if (matches.isEmpty()) continue

            val plainSentence = HTML_TAG_REGEX.replace(rawPara, "").trim()
            if (plainSentence.length < 18) continue

            for (match in matches) {
                val phraseRaw = match.groups[2]?.value ?: match.groups[3]?.value ?: continue
                val cleanPhrase = HTML_TAG_REGEX.replace(phraseRaw, "").trim()
                if (cleanPhrase.length !in 2..90) continue

                val clozeText = plainSentence.replaceFirst(cleanPhrase, " [ ... ؟ ... ] ")
                results.add(
                    ClozeCardEntity(
                        cardId = "cloze_${siteQuestionId}_$cardIndex",
                        siteQuestionId = siteQuestionId,
                        sourcePromptHtml = plainSentence,
                        clozeDeletionText = clozeText,
                        answerKeyPhrase = cleanPhrase,
                        categoryTitle = categoryTitle,
                        repetitions = 0,
                        easinessFactor = 2.5f,
                        intervalDays = 0,
                        dueEpochDay = todayEpochDay,
                        suppressed = false
                    )
                )
                cardIndex++
                if (cardIndex >= 5) break
            }
            if (cardIndex >= 5) break
        }
        return results
    }
}

/**
 * Category Tree Flattener supporting:
 * - Two tabs: "بانک من" (only branches holding downloaded questions) and "همه دسته‌ها" (full tree)
 * - Disambiguated full path when category names repeat across different branches
 * - Expand/collapse that hides all descendants when an ancestor is collapsed in BOTH tabs
 */
object CategoryTreeFlattener {

    data class FlattenedTreeRow(
        val node: CategoryNodeEntity,
        val hasChildren: Boolean,
        val isExpanded: Boolean,
        val displayTitle: String,
        val showDisambiguatedPath: Boolean,
        val questionCount: Int,
        val answeredCount: Int,
        val progressFraction: Float
    )

    fun flattenTree(
        allNodes: List<CategoryNodeEntity>,
        expandedNodeIds: Set<String>,
        onlyDownloadedTab: Boolean,
        nodeQuestionCounts: Map<String, Int>,
        nodeAnsweredCounts: Map<String, Int>
    ): List<FlattenedTreeRow> {
        val visibleNodes = if (onlyDownloadedTab) {
            // Keep a node if it isDownloaded OR any of its descendants isDownloaded
            val byParent = allNodes.groupBy { it.parentId }
            val activeIds = mutableSetOf<String>()
            fun checkBranch(node: CategoryNodeEntity): Boolean {
                val children = byParent[node.id].orEmpty()
                var anyChildDownloaded = false
                for (c in children) {
                    if (checkBranch(c)) anyChildDownloaded = true
                }
                val keep = node.isDownloaded || anyChildDownloaded
                if (keep) activeIds.add(node.id)
                return keep
            }
            allNodes.filter { it.parentId == null }.forEach { checkBranch(it) }
            allNodes.filter { it.id in activeIds }
        } else {
            allNodes
        }

        // Detect repeated names across visible nodes to show full path disambiguation
        val nameFrequency = visibleNodes.groupingBy { it.name.trim() }.eachCount()
        val childrenByParent = visibleNodes.groupBy { it.parentId }
        val result = mutableListOf<FlattenedTreeRow>()

        fun traverse(parentId: String?) {
            val children = childrenByParent[parentId]?.sortedBy { it.orderIndex }.orEmpty()
            for (node in children) {
                val nodeChildren = childrenByParent[node.id].orEmpty()
                val hasChildren = nodeChildren.isNotEmpty()
                val isExpanded = node.id in expandedNodeIds
                val isDuplicateName = (nameFrequency[node.name.trim()] ?: 0) > 1 && node.depth > 0
                val qCount = nodeQuestionCounts[node.id] ?: node.totalBankQuestionsCount
                val ansCount = nodeAnsweredCounts[node.id] ?: 0
                val progress = if (qCount > 0) (ansCount.toFloat() / qCount.toFloat()).coerceIn(0f, 1f) else 0f

                result.add(
                    FlattenedTreeRow(
                        node = node,
                        hasChildren = hasChildren,
                        isExpanded = isExpanded,
                        displayTitle = if (isDuplicateName) "${node.name} (${node.fullPath})" else node.name,
                        showDisambiguatedPath = isDuplicateName,
                        questionCount = qCount,
                        answeredCount = ansCount,
                        progressFraction = progress
                    )
                )

                // Only recurse into descendants if current node is expanded
                if (hasChildren && isExpanded) {
                    traverse(node.id)
                }
            }
        }

        traverse(null)
        return result
    }
}

/**
 * Trap Question Detector:
 * Identifies questions where any wrong option's vote share strictly exceeds the correct option's vote share.
 */
object TrapDetector {
    fun isTrapQuestion(correctOptionIndex: Int?, optionVotes: List<Int>?): Boolean {
        if (correctOptionIndex == null || optionVotes == null || optionVotes.isEmpty()) return false
        if (correctOptionIndex !in optionVotes.indices) return false
        val correctVote = optionVotes[correctOptionIndex]
        return optionVotes.withIndex().any { (idx, vote) ->
            idx != correctOptionIndex && vote > correctVote
        }
    }
}
