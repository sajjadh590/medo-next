package com.example.domain

import com.example.data.InstalledPackEntity
import com.example.data.UserProgressEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Format v3 Pack Manifest & Delta Update Planner.
 * Computes exact chunks and media hashes that need downloading,
 * handles membership changes, and plans tombstone application (Mirror vs Keep-Local).
 */
object PackDeltaPlanner {

    data class PackDescriptor(
        val id: String,
        val categoryId: String,
        val categoryPath: String,
        val level: String, // "text", "lessons", "media"
        val questionCount: Int,
        val sizeBytes: Long,
        val chunkHashes: List<String>,
        val mediaHashes: List<String>
    )

    data class V3Manifest(
        val formatVersion: Int,
        val bankVersion: String,
        val packs: List<PackDescriptor>,
        val questionRevisions: Map<String, String>,
        val tombstones: List<String>
    )

    data class DeltaPlan(
        val selectedPackIds: List<String>,
        val chunksToDownload: List<String>,
        val chunksAlreadyCached: List<String>,
        val mediaToDownload: List<String>,
        val mediaAlreadyCached: List<String>,
        val questionsToRemoveOrMark: List<String>,
        val mirrorDestructiveDelete: Boolean,
        val estimatedDownloadBytes: Long
    )

    fun parseManifest(jsonString: String): V3Manifest {
        val root = JSONObject(jsonString)
        val formatVersion = root.optInt("formatVersion", 3)
        val bankVersion = root.optString("bankVersion", "2026.11")
        val packsArr = root.optJSONArray("packs") ?: JSONArray()
        val packs = mutableListOf<PackDescriptor>()

        for (i in 0 until packsArr.length()) {
            val obj = packsArr.getJSONObject(i)
            val chunkArr = obj.optJSONArray("chunkHashes") ?: JSONArray()
            val mediaArr = obj.optJSONArray("mediaHashes") ?: JSONArray()
            val chunks = (0 until chunkArr.length()).map { chunkArr.getString(it) }
            val media = (0 until mediaArr.length()).map { mediaArr.getString(it) }
            packs.add(
                PackDescriptor(
                    id = obj.getString("id"),
                    categoryId = obj.optString("categoryId", "cat_root"),
                    categoryPath = obj.optString("categoryPath", "عمومی"),
                    level = obj.optString("level", "text"),
                    questionCount = obj.optInt("questionCount", 0),
                    sizeBytes = obj.optLong("sizeBytes", 0L),
                    chunkHashes = chunks,
                    mediaHashes = media
                )
            )
        }

        val revObj = root.optJSONObject("questionRevisions") ?: JSONObject()
        val revisions = mutableMapOf<String, String>()
        val keys = revObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            revisions[k] = revObj.getString(k)
        }

        val tombArr = root.optJSONArray("tombstones") ?: JSONArray()
        val tombstones = (0 until tombArr.length()).map { tombArr.getString(it) }

        return V3Manifest(
            formatVersion = formatVersion,
            bankVersion = bankVersion,
            packs = packs,
            questionRevisions = revisions,
            tombstones = tombstones
        )
    }

    fun planDeltaUpdate(
        manifest: V3Manifest,
        selectedPackIds: Set<String>,
        installedPacks: List<InstalledPackEntity>,
        existingMediaHashes: Set<String>,
        mirrorMode: Boolean
    ): DeltaPlan {
        val installedChunkHashes = installedPacks.flatMap { pack ->
            parseJsonStringArray(pack.chunkHashesJson)
        }.toSet()

        val chosenPacks = manifest.packs.filter { it.id in selectedPackIds }
        val neededChunks = mutableListOf<String>()
        val cachedChunks = mutableListOf<String>()
        val neededMedia = mutableListOf<String>()
        val cachedMedia = mutableListOf<String>()
        var bytesEstimate = 0L

        for (pack in chosenPacks) {
            val totalPackItems = (pack.chunkHashes.size + pack.mediaHashes.size).coerceAtLeast(1)
            val perItemBytes = pack.sizeBytes / totalPackItems

            for (ch in pack.chunkHashes) {
                if (ch in installedChunkHashes) {
                    cachedChunks.add(ch)
                } else if (ch !in neededChunks) {
                    neededChunks.add(ch)
                    bytesEstimate += perItemBytes
                }
            }
            for (mh in pack.mediaHashes) {
                if (mh in existingMediaHashes) {
                    cachedMedia.add(mh)
                } else if (mh !in neededMedia) {
                    neededMedia.add(mh)
                    bytesEstimate += perItemBytes
                }
            }
        }

        return DeltaPlan(
            selectedPackIds = chosenPacks.map { it.id },
            chunksToDownload = neededChunks,
            chunksAlreadyCached = cachedChunks,
            mediaToDownload = neededMedia,
            mediaAlreadyCached = cachedMedia,
            questionsToRemoveOrMark = manifest.tombstones,
            mirrorDestructiveDelete = mirrorMode,
            estimatedDownloadBytes = bytesEstimate
        )
    }

    fun parseJsonStringArray(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * Progress Backup & Restore Serializer (`docs/progress-format.md`)
 * + Legacy App Storage Migrator (`ir.medo.app` SharedPreferences / JSON store).
 */
object ProgressBackupManager {

    fun exportProgressToJson(
        records: List<UserProgressEntity>,
        appVersion: String = "2.0.0-next"
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("appVersion", appVersion)

        val arr = JSONArray()
        for (r in records) {
            val item = JSONObject()
            item.put("siteQuestionId", r.siteQuestionId)
            item.put("contentHash", r.contentHash)
            if (r.lastSelectedOption != null) item.put("lastSelectedOption", r.lastSelectedOption)
            if (r.isCorrect != null) item.put("isCorrect", r.isCorrect)
            item.put("attemptsCount", r.attemptsCount)
            item.put("wrongCount", r.wrongCount)
            item.put("bookmarked", r.bookmarked)
            item.put("starred", r.starred)
            item.put("flagged", r.flagged)
            item.put("personalNote", r.personalNote)
            item.put("highlightsJson", r.highlightsJson)
            item.put("totalTimeSpentMs", r.totalTimeSpentMs)
            item.put("lastAnsweredAt", r.lastAnsweredAt)

            val sm2 = JSONObject()
            sm2.put("repetitions", r.sm2Repetitions)
            sm2.put("easinessFactor", r.sm2EasinessFactor.toDouble())
            sm2.put("intervalDays", r.sm2IntervalDays)
            sm2.put("dueDateEpochDay", r.sm2DueEpochDay)
            sm2.put("suppressed", r.sm2Suppressed)
            item.put("sm2", sm2)

            arr.put(item)
        }
        root.put("records", arr)
        return root.toString(2)
    }

    fun importProgressFromJson(
        jsonString: String,
        existingByQuestionId: Map<String, UserProgressEntity> = emptyMap()
    ): List<UserProgressEntity> {
        val root = JSONObject(jsonString)
        val arr = root.optJSONArray("records") ?: JSONArray()
        val merged = mutableListOf<UserProgressEntity>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val qId = obj.getString("siteQuestionId")
            val existing = existingByQuestionId[qId]
            val incomingLastAnswered = obj.optLong("lastAnsweredAt", 0L)
            val sm2Obj = obj.optJSONObject("sm2")

            val incoming = UserProgressEntity(
                siteQuestionId = qId,
                contentHash = obj.optString("contentHash", existing?.contentHash ?: ""),
                lastSelectedOption = if (obj.has("lastSelectedOption") && !obj.isNull("lastSelectedOption")) obj.getInt("lastSelectedOption") else null,
                isCorrect = if (obj.has("isCorrect") && !obj.isNull("isCorrect")) obj.getBoolean("isCorrect") else null,
                attemptsCount = obj.optInt("attemptsCount", 0),
                wrongCount = obj.optInt("wrongCount", 0),
                bookmarked = obj.optBoolean("bookmarked", false),
                starred = obj.optBoolean("starred", false),
                flagged = obj.optBoolean("flagged", false),
                personalNote = obj.optString("personalNote", ""),
                highlightsJson = obj.optString("highlightsJson", "[]"),
                totalTimeSpentMs = obj.optLong("totalTimeSpentMs", 0L),
                lastAnsweredAt = incomingLastAnswered,
                sm2Repetitions = sm2Obj?.optInt("repetitions", 0) ?: 0,
                sm2EasinessFactor = sm2Obj?.optDouble("easinessFactor", 2.5)?.toFloat() ?: 2.5f,
                sm2IntervalDays = sm2Obj?.optInt("intervalDays", 0) ?: 0,
                sm2DueEpochDay = sm2Obj?.optLong("dueDateEpochDay", 0L) ?: 0L,
                sm2Suppressed = sm2Obj?.optBoolean("suppressed", false) ?: false
            )

            if (existing == null) {
                merged.add(incoming)
            } else {
                val useIncomingAnswer = incoming.lastAnsweredAt >= existing.lastAnsweredAt
                merged.add(
                    UserProgressEntity(
                        siteQuestionId = qId,
                        contentHash = incoming.contentHash.ifBlank { existing.contentHash },
                        lastSelectedOption = if (useIncomingAnswer) (incoming.lastSelectedOption ?: existing.lastSelectedOption) else existing.lastSelectedOption,
                        isCorrect = if (useIncomingAnswer) (incoming.isCorrect ?: existing.isCorrect) else existing.isCorrect,
                        attemptsCount = maxOf(incoming.attemptsCount, existing.attemptsCount),
                        wrongCount = maxOf(incoming.wrongCount, existing.wrongCount),
                        bookmarked = incoming.bookmarked || existing.bookmarked,
                        starred = incoming.starred || existing.starred,
                        flagged = incoming.flagged || existing.flagged,
                        personalNote = incoming.personalNote.ifBlank { existing.personalNote },
                        highlightsJson = if (incoming.highlightsJson != "[]") incoming.highlightsJson else existing.highlightsJson,
                        totalTimeSpentMs = maxOf(incoming.totalTimeSpentMs, existing.totalTimeSpentMs),
                        lastAnsweredAt = maxOf(incoming.lastAnsweredAt, existing.lastAnsweredAt),
                        sm2Repetitions = if (useIncomingAnswer) incoming.sm2Repetitions else existing.sm2Repetitions,
                        sm2EasinessFactor = if (useIncomingAnswer) incoming.sm2EasinessFactor else existing.sm2EasinessFactor,
                        sm2IntervalDays = if (useIncomingAnswer) incoming.sm2IntervalDays else existing.sm2IntervalDays,
                        sm2DueEpochDay = if (useIncomingAnswer) incoming.sm2DueEpochDay else existing.sm2DueEpochDay,
                        sm2Suppressed = incoming.sm2Suppressed || existing.sm2Suppressed
                    )
                )
            }
        }
        return merged
    }

    /**
     * Migrates legacy old-app (`ir.medo.app`) progress map (e.g. from SharedPreferences or old JSON store)
     * into `UserProgressEntity` records.
     */
    fun migrateFromLegacyStore(legacyEntries: Map<String, Any?>): List<UserProgressEntity> {
        val byQuestion = mutableMapOf<String, UserProgressEntity>()

        for ((key, value) in legacyEntries) {
            when {
                key.startsWith("ans_") -> {
                    val qId = key.removePrefix("ans_")
                    val rawStr = value?.toString() ?: continue
                    // Old format: "selectedIdx:isCorrect(1/0)" or integer index
                    val parts = rawStr.split(":")
                    val selected = parts.getOrNull(0)?.toIntOrNull()
                    val correct = parts.getOrNull(1)?.let { it == "1" || it.equals("true", true) }
                    val cur = byQuestion[qId] ?: UserProgressEntity(siteQuestionId = qId)
                    byQuestion[qId] = cur.copy(
                        lastSelectedOption = selected,
                        isCorrect = correct,
                        attemptsCount = maxOf(1, cur.attemptsCount),
                        wrongCount = if (correct == false) maxOf(1, cur.wrongCount) else cur.wrongCount,
                        lastAnsweredAt = System.currentTimeMillis()
                    )
                }
                key.startsWith("bm_") -> {
                    val qId = key.removePrefix("bm_")
                    val isBm = value == true || value?.toString() == "1" || value?.toString() == "true"
                    val cur = byQuestion[qId] ?: UserProgressEntity(siteQuestionId = qId)
                    byQuestion[qId] = cur.copy(bookmarked = isBm)
                }
                key.startsWith("note_") -> {
                    val qId = key.removePrefix("note_")
                    val note = value?.toString().orEmpty()
                    val cur = byQuestion[qId] ?: UserProgressEntity(siteQuestionId = qId)
                    byQuestion[qId] = cur.copy(personalNote = note)
                }
            }
        }
        return byQuestion.values.toList()
    }
}
