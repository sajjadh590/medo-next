package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MedoDao {

    @Query("SELECT * FROM category_nodes ORDER BY orderIndex ASC")
    fun observeAllCategoryNodes(): Flow<List<CategoryNodeEntity>>

    @Query("SELECT * FROM category_nodes ORDER BY orderIndex ASC")
    suspend fun getAllCategoryNodes(): List<CategoryNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryNodes(nodes: List<CategoryNodeEntity>)

    @Query("SELECT * FROM questions ORDER BY orderInNode ASC")
    fun observeAllQuestions(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions ORDER BY orderInNode ASC")
    suspend fun getAllQuestions(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE siteQuestionId = :id LIMIT 1")
    suspend fun getQuestionById(id: String): QuestionEntity?

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN question_node_cross_ref ref ON q.siteQuestionId = ref.siteQuestionId
        WHERE ref.nodeId IN (:nodeIds)
        GROUP BY q.siteQuestionId
        ORDER BY q.orderInNode ASC
        """
    )
    suspend fun getQuestionsForNodes(nodeIds: List<String>): List<QuestionEntity>

    @Query(
        """
        SELECT * FROM questions
        WHERE normalizedSearchText LIKE '%' || :normalizedQuery || '%'
        ORDER BY orderInNode ASC
        LIMIT 250
        """
    )
    suspend fun searchQuestions(normalizedQuery: String): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionNodeCrossRefs(refs: List<QuestionNodeCrossRef>)

    @Query("SELECT * FROM question_node_cross_ref")
    fun observeAllCrossRefs(): Flow<List<QuestionNodeCrossRef>>

    @Query("SELECT * FROM question_node_cross_ref")
    suspend fun getAllCrossRefs(): List<QuestionNodeCrossRef>

    // User Progress (never deleted during content bank re-import)
    @Query("SELECT * FROM user_progress")
    fun observeAllProgress(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress")
    suspend fun getAllProgress(): List<UserProgressEntity>

    @Query("SELECT * FROM user_progress WHERE siteQuestionId = :id LIMIT 1")
    suspend fun getProgressForQuestion(id: String): UserProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: UserProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgressList(progressList: List<UserProgressEntity>)

    // Cloze flashcards
    @Query("SELECT * FROM cloze_cards WHERE suppressed = 0 ORDER BY dueEpochDay ASC")
    fun observeActiveClozeCards(): Flow<List<ClozeCardEntity>>

    @Query("SELECT * FROM cloze_cards")
    suspend fun getAllClozeCards(): List<ClozeCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClozeCards(cards: List<ClozeCardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClozeCard(card: ClozeCardEntity)

    // Exam Sessions
    @Query("SELECT * FROM exam_sessions ORDER BY createdAt DESC")
    fun observeExamSessions(): Flow<List<ExamSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamSession(session: ExamSessionEntity): Long

    // Packs & Media
    @Query("SELECT * FROM installed_packs ORDER BY installedAt DESC")
    fun observeInstalledPacks(): Flow<List<InstalledPackEntity>>

    @Query("SELECT * FROM installed_packs")
    suspend fun getInstalledPacks(): List<InstalledPackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacks(packs: List<InstalledPackEntity>)

    @Query("DELETE FROM installed_packs WHERE packId = :packId")
    suspend fun deletePackById(packId: String)

    @Query("DELETE FROM questions WHERE packId = :packId")
    suspend fun deleteQuestionsByPackId(packId: String)

    @Query("UPDATE questions SET removedUpstream = 1 WHERE siteQuestionId IN (:tombstoneIds)")
    suspend fun markQuestionsRemovedUpstream(tombstoneIds: List<String>)

    @Query("DELETE FROM questions WHERE siteQuestionId IN (:tombstoneIds)")
    suspend fun deleteQuestionsByIds(tombstoneIds: List<String>)

    @Query("SELECT * FROM media_blobs")
    fun observeMediaBlobs(): Flow<List<MediaBlobEntity>>

    @Query("SELECT * FROM media_blobs WHERE mediaHash = :hash LIMIT 1")
    suspend fun getMediaBlob(hash: String): MediaBlobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaBlobs(blobs: List<MediaBlobEntity>)

    @Transaction
    suspend fun atomicImportBankChunk(
        nodes: List<CategoryNodeEntity>,
        questions: List<QuestionEntity>,
        crossRefs: List<QuestionNodeCrossRef>,
        packs: List<InstalledPackEntity>,
        mediaBlobs: List<MediaBlobEntity>,
        clozeCards: List<ClozeCardEntity>
    ) {
        if (nodes.isNotEmpty()) insertCategoryNodes(nodes)
        if (questions.isNotEmpty()) insertQuestions(questions)
        if (crossRefs.isNotEmpty()) insertQuestionNodeCrossRefs(crossRefs)
        if (packs.isNotEmpty()) insertPacks(packs)
        if (mediaBlobs.isNotEmpty()) insertMediaBlobs(mediaBlobs)
        if (clozeCards.isNotEmpty()) insertClozeCards(clozeCards)
    }
}
