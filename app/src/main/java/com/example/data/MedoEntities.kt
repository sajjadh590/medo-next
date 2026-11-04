package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_nodes",
    indices = [Index("parentId"), Index("orderIndex")]
)
data class CategoryNodeEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val fullPath: String,
    val depth: Int,
    val orderIndex: Int,
    val totalBankQuestionsCount: Int,
    val isDownloaded: Boolean
)

@Entity(
    tableName = "questions",
    indices = [
        Index("normalizedSearchText"),
        Index("hasKey"),
        Index("hasLesson"),
        Index("hasMindMap"),
        Index("isTrap"),
        Index("packId")
    ]
)
data class QuestionEntity(
    @PrimaryKey val siteQuestionId: String,
    val contentHash: String,
    val stemHtml: String,
    val optionsJson: String, // JSON array of option strings
    val optionVotesJson: String?, // Nullable JSON array of vote percentages [15, 62, 18, 5]
    val correctOptionIndex: Int?, // Nullable if key is missing in incomplete extraction
    val explanationHtml: String?,
    val lessonHtml: String?,
    val mindMapMediaHash: String?,
    val stemImageMediaHash: String?,
    val drugSafetyMetaJson: String?, // Structured clinical JSON: Pregnancy Category, Renal Adjustment (CrCl/GFR), Dose Range
    val normalizedSearchText: String,
    val hasKey: Boolean,
    val hasLesson: Boolean,
    val hasMindMap: Boolean,
    val isTrap: Boolean, // True when a wrong option's vote share > correct option's vote share
    val removedUpstream: Boolean = false,
    val packId: String = "pack_default",
    val orderInNode: Int = 0
)

@Entity(
    tableName = "question_node_cross_ref",
    primaryKeys = ["siteQuestionId", "nodeId"],
    indices = [Index("nodeId"), Index("siteQuestionId")]
)
data class QuestionNodeCrossRef(
    val siteQuestionId: String,
    val nodeId: String
)

/**
 * User progress is stored in a dedicated table keyed by [siteQuestionId]
 * so re-importing banks or applying v3 delta updates NEVER loses user answers,
 * bookmarks, personal notes, highlights, or SM-2 spaced repetition state.
 */
@Entity(
    tableName = "user_progress",
    indices = [
        Index("bookmarked"),
        Index("starred"),
        Index("flagged"),
        Index("isCorrect"),
        Index("sm2DueEpochDay")
    ]
)
data class UserProgressEntity(
    @PrimaryKey val siteQuestionId: String,
    val contentHash: String = "",
    val lastSelectedOption: Int? = null,
    val isCorrect: Boolean? = null,
    val attemptsCount: Int = 0,
    val wrongCount: Int = 0,
    val bookmarked: Boolean = false,
    val starred: Boolean = false,
    val flagged: Boolean = false,
    val personalNote: String = "",
    val highlightsJson: String = "[]",
    val totalTimeSpentMs: Long = 0L,
    val lastAnsweredAt: Long = 0L,
    // SM-2 Spaced Repetition fields
    val sm2Repetitions: Int = 0,
    val sm2EasinessFactor: Float = 2.5f,
    val sm2IntervalDays: Int = 0,
    val sm2DueEpochDay: Long = 0L,
    val sm2Suppressed: Boolean = false
)

@Entity(
    tableName = "cloze_cards",
    indices = [Index("siteQuestionId"), Index("dueEpochDay")]
)
data class ClozeCardEntity(
    @PrimaryKey val cardId: String,
    val siteQuestionId: String,
    val sourcePromptHtml: String,
    val clozeDeletionText: String,
    val answerKeyPhrase: String,
    val categoryTitle: String,
    val repetitions: Int = 0,
    val easinessFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val dueEpochDay: Long = 0L,
    val suppressed: Boolean = false
)

@Entity(tableName = "exam_sessions")
data class ExamSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0L,
    val title: String,
    val scopeNodeId: String?,
    val createdAt: Long,
    val finishedAt: Long?,
    val durationLimitSeconds: Int,
    val elapsedSeconds: Int,
    val negativeMarkingDivisor: Int, // e.g. 3 for 1/3 negative marking, 0 for off
    val questionIdsJson: String,
    val answersMapJson: String, // {"q_101": 2, "q_102": 0}
    val flaggedIdsJson: String, // ["q_102"]
    val rawScorePercent: Float = 0f,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val unansweredCount: Int = 0,
    val isCompleted: Boolean = false
)

@Entity(tableName = "installed_packs")
data class InstalledPackEntity(
    @PrimaryKey val packId: String,
    val categoryId: String,
    val categoryTitle: String,
    val level: String, // "text", "lessons", "media"
    val version: String,
    val questionCount: Int,
    val sizeBytes: Long,
    val chunkHashesJson: String,
    val mediaHashesJson: String,
    val installedAt: Long,
    val updateAvailable: Boolean = false
)

@Entity(tableName = "media_blobs")
data class MediaBlobEntity(
    @PrimaryKey val mediaHash: String,
    val mimeType: String,
    val localFilePath: String,
    val widthPx: Int,
    val heightPx: Int,
    val sizeBytes: Long,
    val titleFa: String
)
