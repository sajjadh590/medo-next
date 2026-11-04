package com.example.data

import com.example.domain.ClozeGenerator
import com.example.domain.TrapDetector
import com.example.util.PersianTextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Provides:
 * 1. Synthetic test/demo dataset strictly labelled «[نمونه آزمایشی]» for debug/preview builds only.
 *    Release builds start completely empty (`shouldSeedDemoData(isDebugBuild = false) == false`) and import user banks.
 *    Contains zero model-authored drug safety JSON cards (`drugSafetyMetaJson = null`).
 * 2. `.medo` v2 ZIP archive parser and synthetic `.medo` v2 fixture builder for round-trip verification.
 * 3. Synthetic 20,000-question benchmark generator (`generateSyntheticLargeBank(count = 20_000)`) for scale testing.
 */
object SyntheticBankSeedAndImporter {

    const val DEMO_LABEL = "[نمونه آزمایشی]"

    data class ParsedBankBundle(
        val nodes: List<CategoryNodeEntity>,
        val questions: List<QuestionEntity>,
        val crossRefs: List<QuestionNodeCrossRef>,
        val packs: List<InstalledPackEntity>,
        val mediaBlobs: List<MediaBlobEntity>,
        val clozeCards: List<ClozeCardEntity>
    )

    /**
     * Enforces Section B rule: Release builds start empty and import user `.medo` banks;
     * synthetic sample questions are only seeded in debug/test builds and explicitly labelled «[نمونه آزمایشی]».
     */
    fun shouldSeedDemoData(isDebugBuild: Boolean): Boolean = isDebugBuild

    fun buildInitialClinicalSeed(todayEpochDay: Long = 20762L): ParsedBankBundle {
        val nodes = listOf(
            CategoryNodeEntity(
                id = "cat_dastyari",
                parentId = null,
                name = "دستیاری پزشکی $DEMO_LABEL",
                fullPath = "دستیاری پزشکی $DEMO_LABEL",
                depth = 0,
                orderIndex = 1,
                totalBankQuestionsCount = 12,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_dastyari_internal",
                parentId = "cat_dastyari",
                name = "بیماری‌های داخلی",
                fullPath = "دستیاری پزشکی > بیماری‌های داخلی",
                depth = 1,
                orderIndex = 2,
                totalBankQuestionsCount = 8,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_dastyari_cardio",
                parentId = "cat_dastyari_internal",
                name = "قلب و عروق",
                fullPath = "دستیاری پزشکی > بیماری‌های داخلی > قلب و عروق",
                depth = 2,
                orderIndex = 3,
                totalBankQuestionsCount = 4,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_dastyari_nephro",
                parentId = "cat_dastyari_internal",
                name = "نفرولوژی و الکترولیت‌ها",
                fullPath = "دستیاری پزشکی > بیماری‌های داخلی > نفرولوژی و الکترولیت‌ها",
                depth = 2,
                orderIndex = 4,
                totalBankQuestionsCount = 4,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_dastyari_pharma",
                parentId = "cat_dastyari",
                name = "فارماکولوژی و مباحث دارویی",
                fullPath = "دستیاری پزشکی > فارماکولوژی و مباحث دارویی",
                depth = 1,
                orderIndex = 5,
                totalBankQuestionsCount = 4,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_pre_intern",
                parentId = null,
                name = "پره انترنی $DEMO_LABEL",
                fullPath = "پره انترنی $DEMO_LABEL",
                depth = 0,
                orderIndex = 6,
                totalBankQuestionsCount = 6,
                isDownloaded = true
            ),
            // Duplicate name "قلب و عروق" under Pre-internship to verify full-path disambiguation
            CategoryNodeEntity(
                id = "cat_pre_cardio",
                parentId = "cat_pre_intern",
                name = "قلب و عروق",
                fullPath = "پره انترنی > قلب و عروق",
                depth = 1,
                orderIndex = 7,
                totalBankQuestionsCount = 3,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_pre_emergency",
                parentId = "cat_pre_intern",
                name = "طب اورژانس",
                fullPath = "پره انترنی > طب اورژانس",
                depth = 1,
                orderIndex = 8,
                totalBankQuestionsCount = 3,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_talifi",
                parentId = null,
                name = "تالیفی $DEMO_LABEL",
                fullPath = "تالیفی $DEMO_LABEL",
                depth = 0,
                orderIndex = 9,
                totalBankQuestionsCount = 5,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_talifi_infectious",
                parentId = "cat_talifi",
                name = "بیماری‌های عفونی",
                fullPath = "تالیفی > بیماری‌های عفونی",
                depth = 1,
                orderIndex = 10,
                totalBankQuestionsCount = 5,
                isDownloaded = true
            ),
            CategoryNodeEntity(
                id = "cat_uninstalled_radiology",
                parentId = null,
                name = "رادیولوژی (بسته دانلود نشده)",
                fullPath = "رادیولوژی (بسته دانلود نشده)",
                depth = 0,
                orderIndex = 11,
                totalBankQuestionsCount = 140,
                isDownloaded = false
            )
        )

        val rawQuestions = listOf(
            createSyntheticQuestion(
                id = "q_1001",
                nodeIds = listOf("cat_dastyari", "cat_dastyari_internal", "cat_dastyari_cardio", "cat_pre_intern", "cat_pre_cardio"),
                stemHtml = """
                    <p><b>$DEMO_LABEL</b> سوال آزمایشی ۱ (کد مرجع ۱۰۰۱): بررسی چیدمان متن، جدول، و مقادیر آزمایشگاهی چندستونی در نمای تمرین و خروجی PDF.</p>
                    <p>Param-A: 86/54</p>
                    <p>Param-B: 112</p>
                    <p>Param-C: 91%</p>
                    <p>Param-D: 14.2</p>
                    <p>Param-E: 1.8</p>
                    <p>Param-F: 4.6</p>
                    <p>کدام گزینه در این نمونه آزمایشی به عنوان گزینه صحیح (اندیس ۲) تعریف شده است؟</p>
                """.trimIndent(),
                options = listOf(
                    "$DEMO_LABEL گزینه الف (نمونه آزمایشی اول)",
                    "$DEMO_LABEL گزینه ب (پاسخ صحیح نمونه آزمایشی)",
                    "$DEMO_LABEL گزینه ج (نمونه آزمایشی سوم)",
                    "$DEMO_LABEL گزینه د (نمونه آزمایشی چهارم)"
                ),
                votes = listOf(22, 65, 9, 4),
                correctIndex = 1,
                explanationHtml = """
                    <p><b>$DEMO_LABEL</b> تشریح نمونه آزمایشی ۱: این متن صرفاً برای آزمایش رندر پاسخنامه، فلش‌کارت‌های <b>عبارت کلیدی اول</b> و <b>عبارت کلیدی دوم</b> تولید شده است. برای مشاهده مایندمپ روی مایندمپ کلیک کنید.</p>
                """.trimIndent(),
                lessonHtml = """
                    <h2>$DEMO_LABEL درسنامه آزمایشی ۱ (تست جدول عریض و استخراج قطعی)</h2>
                    <p>این درسنامه آزمایشی برای بررسی اسکرول افقی جداول عریض و تولید خودکار کارت‌های Cloze از عبارات <b>کلیدی بولد شده در متن درسنامه</b> ساخته شده است.</p>
                    <table>
                        <thead>
                            <tr>
                                <th>ردیف نمونه</th>
                                <th>پارامتر اول</th>
                                <th>پارامتر دوم</th>
                                <th>مقدار CrCl / GFR آزمایشی</th>
                                <th>رده بارداری آزمایشی</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>نمونه آزمایشی الف</td>
                                <td>مقدار ۱۰۰</td>
                                <td>هر ۱۲ ساعت</td>
                                <td>CrCl &gt;= 30 (مقدار مستخرج از جدول درسنامه)</td>
                                <td>رده آزمایشی B</td>
                            </tr>
                            <tr>
                                <td>نمونه آزمایشی ب</td>
                                <td>مقدار ۵۰</td>
                                <td>هر ۲۴ ساعت</td>
                                <td>CrCl &lt; 30 (مقدار مستخرج از جدول درسنامه)</td>
                                <td>رده آزمایشی C</td>
                            </tr>
                        </tbody>
                    </table>
                """.trimIndent(),
                mindMapHash = "mm_demo_diagram_01",
                packId = "pack_dastyari_text",
                orderInNode = 1
            ),
            // TRAP QUESTION (Wrong option vote 58% > Correct option vote 27%)
            createSyntheticQuestion(
                id = "q_1002",
                nodeIds = listOf("cat_dastyari", "cat_dastyari_internal", "cat_dastyari_nephro"),
                stemHtml = """
                    <p><b>$DEMO_LABEL</b> سوال آزمایشی ۲ (تست فیلتر سوالات دام‌دار و нормаلیزاسیون جستجو: هیپوناترمی، کلیوی، وانکومایسین):</p>
                    <p>Na: 114</p>
                    <p>K: 3.8</p>
                    <p>Cr: 1.4</p>
                    <p>در این سوال آزمایشی درصد آرای گزینه دوم ۵۸٪ و گزینه صحیح سوم ۲۷٪ است تا فیلتر «سوالات دام‌دار» آزمایش شود.</p>
                """.trimIndent(),
                options = listOf(
                    "$DEMO_LABEL گزینه الف (۱۱٪ آرا)",
                    "$DEMO_LABEL گزینه ب (دام تستی آزمایشی - ۵۸٪ آرا)",
                    "$DEMO_LABEL گزینه ج (پاسخ صحیح آزمایشی - ۲۷٪ آرا)",
                    "$DEMO_LABEL گزینه د (۴٪ آرا)"
                ),
                votes = listOf(11, 58, 27, 4),
                correctIndex = 2,
                explanationHtml = """
                    <p><b>$DEMO_LABEL</b> تشریح نمونه آزمایشی ۲: گزینه ج صحیح است و به دلیل بالاتر بودن درصد آرای گزینه ب، این سوال به صورت خودکار در دسته <b>سوالات دام‌دار</b> قرار می‌گیرد.</p>
                """.trimIndent(),
                lessonHtml = """
                    <h2>$DEMO_LABEL درسنامه آزمایشی ۲</h2>
                    <p>متن آزمایشی حاوی <b>عبارت کلیدی سوم برای فلش‌کارت</b> و بررسی جستجوی نرمال‌شده فارسی.</p>
                """.trimIndent(),
                mindMapHash = "mm_demo_diagram_02",
                packId = "pack_dastyari_text",
                orderInNode = 2
            ),
            createSyntheticQuestion(
                id = "q_1003",
                nodeIds = listOf("cat_dastyari", "cat_dastyari_pharma", "cat_talifi", "cat_talifi_infectious"),
                stemHtml = """
                    <p><b>$DEMO_LABEL</b> سوال آزمایشی ۳ (کد ۱۰۰۳ - حاوی کلیدواژه وانکومایسین برای تست جستجو): کدام گزینه در این سوال آزمایشی صحیح است؟</p>
                """.trimIndent(),
                options = listOf(
                    "$DEMO_LABEL گزینه الف آزمایشی",
                    "$DEMO_LABEL گزینه ب آزمایشی (صحیح)",
                    "$DEMO_LABEL گزینه ج آزمایشی",
                    "$DEMO_LABEL گزینه د آزمایشی"
                ),
                votes = listOf(14, 76, 6, 4),
                correctIndex = 1,
                explanationHtml = """
                    <p><b>$DEMO_LABEL</b> تشریح نمونه آزمایشی ۳: متن آزمایشی جهت بررسی <b>استخراج خودکار عبارات بولد</b> در مرور SM-2.</p>
                """.trimIndent(),
                lessonHtml = """
                    <h2>$DEMO_LABEL درسنامه آزمایشی ۳</h2>
                    <p>نکته آزمایشی مستخرج از متن درسنامه: در CrCl &lt; 30 تنظیم فاصله بر اساس جدول درسنامه انجام می‌شود.</p>
                """.trimIndent(),
                mindMapHash = "mm_demo_diagram_03",
                packId = "pack_dastyari_text",
                orderInNode = 3
            ),
            createSyntheticQuestion(
                id = "q_1004",
                nodeIds = listOf("cat_pre_intern", "cat_pre_emergency"),
                stemHtml = """
                    <p><b>$DEMO_LABEL</b> سوال آزمایشی ۴ (پره انترنی - دام تستی دوم): بررسی عملکرد شبیه‌ساز آزمون با نمره منفی ۱/۳.</p>
                """.trimIndent(),
                options = listOf(
                    "$DEMO_LABEL گزینه الف آزمایشی",
                    "$DEMO_LABEL گزینه ب آزمایشی (صحیح - ۳۴٪)",
                    "$DEMO_LABEL گزینه ج آزمایشی (دام تستی - ۴۹٪)",
                    "$DEMO_LABEL گزینه د آزمایشی"
                ),
                votes = listOf(12, 34, 49, 5),
                correctIndex = 1,
                explanationHtml = """
                    <p><b>$DEMO_LABEL</b> تشریح نمونه آزمایشی ۴ برای بررسی کارنامه آزمون.</p>
                """.trimIndent(),
                lessonHtml = """
                    <h2>$DEMO_LABEL درسنامه آزمایشی ۴</h2>
                    <p>حاوی <b>کلیدواژه آزمایشی چهارم</b> برای صف مرور روزانه.</p>
                """.trimIndent(),
                mindMapHash = "mm_demo_diagram_01",
                packId = "pack_pre_intern_text",
                orderInNode = 4
            ),
            // Incomplete questions (missing explanation and mind map) to test «فقط سوال‌های ناقص» filter & compressed PDF missing ranges
            createSyntheticQuestion(
                id = "q_1005",
                nodeIds = listOf("cat_talifi", "cat_talifi_infectious"),
                stemHtml = """
                    <p><b>$DEMO_LABEL</b> سوال آزمایشی ۵ (ناقص - فاقد درسنامه و مایندمپ برای تست فیلتر سوال‌های ناقص و فشرده‌سازی پاسخنامه PDF):</p>
                """.trimIndent(),
                options = listOf(
                    "$DEMO_LABEL گزینه الف آزمایشی",
                    "$DEMO_LABEL گزینه ب آزمایشی (صحیح)",
                    "$DEMO_LABEL گزینه ج آزمایشی",
                    "$DEMO_LABEL گزینه د آزمایشی"
                ),
                votes = listOf(5, 84, 7, 4),
                correctIndex = 1,
                explanationHtml = null,
                lessonHtml = null,
                mindMapHash = null,
                packId = "pack_talifi_text",
                orderInNode = 5
            ),
            createSyntheticQuestion(
                id = "q_1006",
                nodeIds = listOf("cat_talifi", "cat_talifi_infectious"),
                stemHtml = """
                    <p><b>$DEMO_LABEL</b> سوال آزمایشی ۶ (ناقص متوالی با سوال ۵ جهت بررسی خط «سوال‌های ۵ تا ۶: پاسخ تشریحی ثبت نشده» در خروجی PDF):</p>
                """.trimIndent(),
                options = listOf(
                    "$DEMO_LABEL گزینه الف آزمایشی (صحیح)",
                    "$DEMO_LABEL گزینه ب آزمایشی",
                    "$DEMO_LABEL گزینه ج آزمایشی",
                    "$DEMO_LABEL گزینه د آزمایشی"
                ),
                votes = listOf(79, 12, 6, 3),
                correctIndex = 0,
                explanationHtml = null,
                lessonHtml = null,
                mindMapHash = null,
                packId = "pack_talifi_text",
                orderInNode = 6
            )
        )

        val questions = rawQuestions.map { it.first }
        val crossRefs = rawQuestions.flatMap { it.second }

        val clozeCards = questions.flatMap { q ->
            ClozeGenerator.extractClozeCardsFromLesson(
                siteQuestionId = q.siteQuestionId,
                lessonHtml = q.lessonHtml,
                categoryTitle = "نمونه آزمایشی",
                todayEpochDay = todayEpochDay
            )
        }

        val packs = listOf(
            InstalledPackEntity(
                packId = "pack_dastyari_text",
                categoryId = "cat_dastyari",
                categoryTitle = "بسته نمونه آزمایشی ۱ (متن + درسنامه + مایندمپ)",
                level = "media",
                version = "2026.11-demo",
                questionCount = 3,
                sizeBytes = 18_450_000L,
                chunkHashesJson = "[\"chunk_dastyari_01\",\"chunk_dastyari_02\"]",
                mediaHashesJson = "[\"mm_demo_diagram_01\",\"mm_demo_diagram_02\",\"mm_demo_diagram_03\"]",
                installedAt = System.currentTimeMillis() - 86_400_000L,
                updateAvailable = false
            ),
            InstalledPackEntity(
                packId = "pack_pre_intern_text",
                categoryId = "cat_pre_intern",
                categoryTitle = "بسته نمونه آزمایشی ۲ (پره انترنی)",
                level = "lessons",
                version = "2026.10-demo",
                questionCount = 1,
                sizeBytes = 6_200_000L,
                chunkHashesJson = "[\"chunk_pre_01\"]",
                mediaHashesJson = "[\"mm_demo_diagram_01\"]",
                installedAt = System.currentTimeMillis() - 172_800_000L,
                updateAvailable = true
            ),
            InstalledPackEntity(
                packId = "pack_talifi_text",
                categoryId = "cat_talifi",
                categoryTitle = "بسته نمونه آزمایشی ۳ (تالیفی)",
                level = "text",
                version = "2026.11-demo",
                questionCount = 2,
                sizeBytes = 2_150_000L,
                chunkHashesJson = "[\"chunk_talifi_01\"]",
                mediaHashesJson = "[]",
                installedAt = System.currentTimeMillis() - 43_200_000L,
                updateAvailable = false
            )
        )

        val mediaBlobs = listOf(
            MediaBlobEntity(
                mediaHash = "mm_demo_diagram_01",
                mimeType = "image/png",
                localFilePath = "procedural://mm_demo_diagram_01",
                widthPx = 3840,
                heightPx = 2880,
                sizeBytes = 4_200_000L,
                titleFa = "$DEMO_LABEL نمودار درختی آزمایشی ۱ (۱۱ مگاپیکسل)"
            ),
            MediaBlobEntity(
                mediaHash = "mm_demo_diagram_02",
                mimeType = "image/png",
                localFilePath = "procedural://mm_demo_diagram_02",
                widthPx = 3600,
                heightPx = 2700,
                sizeBytes = 3_800_000L,
                titleFa = "$DEMO_LABEL نمودار درختی آزمایشی ۲"
            ),
            MediaBlobEntity(
                mediaHash = "mm_demo_diagram_03",
                mimeType = "image/png",
                localFilePath = "procedural://mm_demo_diagram_03",
                widthPx = 3600,
                heightPx = 2600,
                sizeBytes = 3_500_000L,
                titleFa = "$DEMO_LABEL نمودار درختی آزمایشی ۳"
            )
        )

        return ParsedBankBundle(
            nodes = nodes,
            questions = questions,
            crossRefs = crossRefs,
            packs = packs,
            mediaBlobs = mediaBlobs,
            clozeCards = clozeCards
        )
    }

    /**
     * Generates a synthetic 20,000-question bank for Section D performance benchmarking
     * (import time, home tree flatten, normalized search latency, and memory usage).
     */
    fun generateSyntheticLargeBank(questionCount: Int = 20_000): List<QuestionEntity> {
        val optionsJson = "[\"گزینه الف آزمایشی\",\"گزینه ب آزمایشی\",\"گزینه ج آزمایشی\",\"گزینه د آزمایشی\"]"
        return ArrayList<QuestionEntity>(questionCount).apply {
            for (i in 1..questionCount) {
                val stem = "$DEMO_LABEL سوال سنتتیک شماره $i برای بنچمارک ظرفیت ۲۰ هزار سوالی"
                val norm = "نمونه ازمایشی سوال سنتتیک شماره $i برای بنچمارک ظرفیت 20 هزار سوالی" +
                    if (i % 500 == 0) " کلیدواژه ویژه بنچمارک" else ""
                add(
                    QuestionEntity(
                        siteQuestionId = "q_bench_$i",
                        contentHash = "hash_$i",
                        stemHtml = "<p>$stem</p>",
                        optionsJson = optionsJson,
                        optionVotesJson = if (i % 10 == 0) "[15,55,20,10]" else null,
                        correctOptionIndex = i % 4,
                        explanationHtml = if (i % 3 == 0) "<p>تشریح آزمایشی $i</p>" else null,
                        lessonHtml = null,
                        mindMapMediaHash = null,
                        stemImageMediaHash = null,
                        drugSafetyMetaJson = null,
                        normalizedSearchText = norm,
                        hasKey = true,
                        hasLesson = (i % 3 == 0),
                        hasMindMap = false,
                        isTrap = (i % 10 == 0),
                        removedUpstream = false,
                        packId = "pack_bench",
                        orderInNode = i
                    )
                )
            }
        }
    }

    private fun createSyntheticQuestion(
        id: String,
        nodeIds: List<String>,
        stemHtml: String,
        options: List<String>,
        votes: List<Int>?,
        correctIndex: Int?,
        explanationHtml: String?,
        lessonHtml: String?,
        mindMapHash: String?,
        packId: String,
        orderInNode: Int
    ): Pair<QuestionEntity, List<QuestionNodeCrossRef>> {
        val optionsJson = JSONArray(options).toString()
        val votesJson = votes?.let { JSONArray(it).toString() }
        val combinedRaw = buildString {
            append(stemHtml).append(" ")
            options.forEach { append(it).append(" ") }
            if (explanationHtml != null) append(explanationHtml).append(" ")
            if (lessonHtml != null) append(lessonHtml)
        }
        val normalizedSearch = PersianTextUtils.normalizeForSearch(combinedRaw)
        val contentHash = sha256Hex("$stemHtml|$optionsJson")
        val isTrap = TrapDetector.isTrapQuestion(correctIndex, votes)

        val entity = QuestionEntity(
            siteQuestionId = id,
            contentHash = contentHash,
            stemHtml = stemHtml,
            optionsJson = optionsJson,
            optionVotesJson = votesJson,
            correctOptionIndex = correctIndex,
            explanationHtml = explanationHtml,
            lessonHtml = lessonHtml,
            mindMapMediaHash = mindMapHash,
            stemImageMediaHash = null,
            drugSafetyMetaJson = null, // Zero model-invented medical data
            normalizedSearchText = normalizedSearch,
            hasKey = correctIndex != null,
            hasLesson = !lessonHtml.isNullOrBlank() || !explanationHtml.isNullOrBlank(),
            hasMindMap = !mindMapHash.isNullOrBlank(),
            isTrap = isTrap,
            removedUpstream = false,
            packId = packId,
            orderInNode = orderInNode
        )

        val refs = nodeIds.map { nodeId ->
            QuestionNodeCrossRef(siteQuestionId = id, nodeId = nodeId)
        }
        return entity to refs
    }

    fun buildMedoV2ZipBytes(bundle: ParsedBankBundle): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val manifest = JSONObject().apply {
                put("formatVersion", 2)
                put("questionCount", bundle.questions.size)
            }
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toString().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            val catArr = JSONArray()
            for (n in bundle.nodes) {
                catArr.put(
                    JSONObject().apply {
                        put("id", n.id)
                        if (n.parentId != null) put("parentId", n.parentId)
                        put("name", n.name)
                        put("fullPath", n.fullPath)
                        put("depth", n.depth)
                        put("orderIndex", n.orderIndex)
                        put("totalBankQuestionsCount", n.totalBankQuestionsCount)
                        put("isDownloaded", n.isDownloaded)
                    }
                )
            }
            zos.putNextEntry(ZipEntry("categories.json"))
            zos.write(catArr.toString().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            val refsByQuestion = bundle.crossRefs.groupBy({ it.siteQuestionId }, { it.nodeId })
            val qArr = JSONArray()
            for (q in bundle.questions) {
                qArr.put(
                    JSONObject().apply {
                        put("siteQuestionId", q.siteQuestionId)
                        put("stemHtml", q.stemHtml)
                        put("options", JSONArray(q.optionsJson))
                        if (q.optionVotesJson != null) put("optionVotes", JSONArray(q.optionVotesJson))
                        if (q.correctOptionIndex != null) put("correctOptionIndex", q.correctOptionIndex)
                        if (q.explanationHtml != null) put("explanationHtml", q.explanationHtml)
                        if (q.lessonHtml != null) put("lessonHtml", q.lessonHtml)
                        if (q.mindMapMediaHash != null) put("mindMapMediaHash", q.mindMapMediaHash)
                        put("nodeIds", JSONArray(refsByQuestion[q.siteQuestionId].orEmpty()))
                        put("orderInNode", q.orderInNode)
                    }
                )
            }
            zos.putNextEntry(ZipEntry("chunks/chunk_000.json"))
            zos.write(qArr.toString().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    fun parseMedoV2ZipBytes(zipBytes: ByteArray, todayEpochDay: Long = 20762L): ParsedBankBundle {
        val nodes = mutableListOf<CategoryNodeEntity>()
        val questions = mutableListOf<QuestionEntity>()
        val crossRefs = mutableListOf<QuestionNodeCrossRef>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val content = zis.readBytes().toString(StandardCharsets.UTF_8)
                when {
                    entry.name == "categories.json" -> {
                        val arr = JSONArray(content)
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            nodes.add(
                                CategoryNodeEntity(
                                    id = o.getString("id"),
                                    parentId = if (o.has("parentId") && !o.isNull("parentId")) o.getString("parentId") else null,
                                    name = o.getString("name"),
                                    fullPath = o.optString("fullPath", o.getString("name")),
                                    depth = o.optInt("depth", 0),
                                    orderIndex = o.optInt("orderIndex", i),
                                    totalBankQuestionsCount = o.optInt("totalBankQuestionsCount", 0),
                                    isDownloaded = o.optBoolean("isDownloaded", true)
                                )
                            )
                        }
                    }
                    entry.name.startsWith("chunks/") && entry.name.endsWith(".json") -> {
                        val arr = JSONArray(content)
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val qId = o.getString("siteQuestionId")
                            val optsArr = o.getJSONArray("options")
                            val opts = (0 until optsArr.length()).map { optsArr.getString(it) }
                            val votesArr = o.optJSONArray("optionVotes")
                            val votes = votesArr?.let { va -> (0 until va.length()).map { va.getInt(it) } }
                            val nodeIdsArr = o.optJSONArray("nodeIds") ?: JSONArray()
                            val nIds = (0 until nodeIdsArr.length()).map { nodeIdsArr.getString(it) }
                            val correctIdx = if (o.has("correctOptionIndex") && !o.isNull("correctOptionIndex")) o.getInt("correctOptionIndex") else null

                            val (qEntity, qRefs) = createSyntheticQuestion(
                                id = qId,
                                nodeIds = nIds,
                                stemHtml = o.getString("stemHtml"),
                                options = opts,
                                votes = votes,
                                correctIndex = correctIdx,
                                explanationHtml = if (o.has("explanationHtml")) o.getString("explanationHtml") else null,
                                lessonHtml = if (o.has("lessonHtml")) o.getString("lessonHtml") else null,
                                mindMapHash = if (o.has("mindMapMediaHash")) o.getString("mindMapMediaHash") else null,
                                packId = "pack_imported_v2",
                                orderInNode = o.optInt("orderInNode", i)
                            )
                            questions.add(qEntity)
                            crossRefs.addAll(qRefs)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (questions.isEmpty()) {
            throw IllegalArgumentException("Corrupt or empty .medo archive: no questions found in chunks")
        }

        val clozeCards = questions.flatMap { q ->
            ClozeGenerator.extractClozeCardsFromLesson(
                siteQuestionId = q.siteQuestionId,
                lessonHtml = q.lessonHtml,
                categoryTitle = "بانک واردشده",
                todayEpochDay = todayEpochDay
            )
        }

        return ParsedBankBundle(
            nodes = nodes,
            questions = questions,
            crossRefs = crossRefs,
            packs = listOf(
                InstalledPackEntity(
                    packId = "pack_imported_v2",
                    categoryId = "cat_root",
                    categoryTitle = "بسته واردشده (.medo v2)",
                    level = "media",
                    version = "v2.0",
                    questionCount = questions.size,
                    sizeBytes = zipBytes.size.toLong(),
                    chunkHashesJson = "[\"chunk_000\"]",
                    mediaHashesJson = "[]",
                    installedAt = System.currentTimeMillis(),
                    updateAvailable = false
                )
            ),
            mediaBlobs = emptyList(),
            clozeCards = clozeCards
        )
    }

    /**
     * Transactional / Atomic Import Guard:
     * Parses and validates the incoming `.medo` archive completely before writing to the DAO.
     * If the archive is corrupt or interrupted, throws an exception and leaves the existing bank untouched.
     */
    suspend fun atomicImportV2ArchiveIntoDao(
        zipBytes: ByteArray,
        dao: MedoDao,
        todayEpochDay: Long = 20762L
    ): ParsedBankBundle {
        val parsed = parseMedoV2ZipBytes(zipBytes, todayEpochDay)
        dao.atomicImportBankChunk(
            nodes = parsed.nodes,
            questions = parsed.questions,
            crossRefs = parsed.crossRefs,
            packs = parsed.packs,
            mediaBlobs = parsed.mediaBlobs,
            clozeCards = parsed.clozeCards
        )
        return parsed
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
