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
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Provides:
 * 1. Synthetic test/demo dataset strictly labelled «[نمونه آزمایشی]» for debug/preview builds only.
 *    Release builds start completely empty (`shouldSeedDemoData(isDebugBuild = false) == false`) and import user banks.
 *    Contains zero model-authored drug safety JSON cards (`drugSafetyMetaJson = null`).
 * 2. Universal `.medo` v2 / ZIP / GZIP / JSON archive parser (`parseMedoV2ZipBytes`) that recognizes:
 *    - Desktop `medo/exporter.py` and `medo/share.py` v2 ZIP bundles (`manifest.json`, `bank.json`, `questions.json`, `categories.json`, `chunks/chunk_NNN.json`)
 *    - Both camelCase and snake_case question/category schemas, nested category trees, and automatic category path synthesis
 * 3. Synthetic 20,000-question benchmark generator and chunked SQLite importer (`importLargeBankToSqliteChunked`).
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
            createSyntheticQuestion(
                id = "q_1002",
                nodeIds = listOf("cat_dastyari", "cat_dastyari_internal", "cat_dastyari_nephro"),
                stemHtml = """
                    <p><b>$DEMO_LABEL</b> سوال آزمایشی ۲ (تست فیلتر سوالات دام‌دار و نرمالیزاسیون جستجو: هیپوناترمی، کلیوی، وانکومایسین):</p>
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
     * Generates a synthetic 20,000-question bank for Section D performance benchmarking.
     */
    fun generateSyntheticLargeBank(questionCount: Int = 20_000): List<QuestionEntity> {
        val optionsJson = "[\"گزینه الف آزمایشی\",\"گزینه ب آزمایشی\",\"گزینه ج آزمایشی\",\"گزینه د آزمایشی\"]"
        return ArrayList<QuestionEntity>(questionCount).apply {
            for (i in 1..questionCount) {
                val stem = "$DEMO_LABEL سوال سنتتیک شماره $i برای بنچمارک ظرفیت ۲۰ هزار سوالی"
                val norm = "نمونه ازمایشی سوال سنتتیک شماره $i برای بنچمارک ظرفیت 20 هزار سوالی" +
                    if (i % 500 == 0) " کلیدواژه ویژه بنچمارک" else ""
                val lesson = if (i % 3 == 0) {
                    "<h2>$DEMO_LABEL درسنامه آزمایشی سوال $i</h2><p>متن درسنامه برای تست بارگذاری تنبل (Lazy Load) از SQLite در سوال $i.</p>"
                } else {
                    null
                }
                add(
                    QuestionEntity(
                        siteQuestionId = "q_bench_$i",
                        contentHash = "hash_$i",
                        stemHtml = "<p>$stem</p>",
                        optionsJson = optionsJson,
                        optionVotesJson = if (i % 10 == 0) "[15,55,20,10]" else null,
                        correctOptionIndex = i % 4,
                        explanationHtml = if (i % 3 == 0) "<p>تشریح آزمایشی $i</p>" else null,
                        lessonHtml = lesson,
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
            drugSafetyMetaJson = null,
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
                put("version", 2)
                put("questionCount", bundle.questions.size)
                put("question_count", bundle.questions.size)
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

    /**
     * Universal `.medo` v2 / ZIP / GZIP / JSON parser:
     * Recognizes:
     * - `.medo` v2 ZIP bundles (`manifest.json`, `bank.json`, `questions.json`, `data.json`, `categories.json`, `chunks/chunk_NNN.json`)
     * - GZIP-compressed `.medo` JSON files
     * - Plain UTF-8 JSON `.medo` files (`{"questions": [...], "categories": [...]}` or `[...]`)
     * - Both camelCase and snake_case question/category schemas from `medo/exporter.py` and `Bank.java`.
     */
    fun parseMedoV2ZipBytes(rawBytes: ByteArray, todayEpochDay: Long = 20762L): ParsedBankBundle {
        if (rawBytes.size < 4) {
            throw IllegalArgumentException("Corrupt or empty .medo archive (size=${rawBytes.size})")
        }

        val nodesMap = linkedMapOf<String, CategoryNodeEntity>()
        val questions = mutableListOf<QuestionEntity>()
        val crossRefs = mutableListOf<QuestionNodeCrossRef>()
        val lessonsById = mutableMapOf<String, String>()
        var bankTitle = "بسته واردشده (.medo v2)"
        var bankVersion = "v2.0"

        val b0 = rawBytes[0].toInt() and 0xFF
        val b1 = rawBytes[1].toInt() and 0xFF
        val isZip = (b0 == 0x50 && b1 == 0x4B) // "PK"
        val isGzip = (b0 == 0x1F && b1 == 0x8B)

        if (isZip) {
            val jsonEntries = mutableListOf<Pair<String, String>>()
            ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.lowercase()
                        if (name.endsWith(".json")) {
                            val text = zis.readBytes().toString(StandardCharsets.UTF_8)
                            jsonEntries.add(entry.name to text)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (jsonEntries.isEmpty()) {
                throw IllegalArgumentException("Corrupt .medo ZIP archive: no JSON entries found")
            }

            // Pass 1: manifest.json, categories.json, lessons.json
            for ((entryName, content) in jsonEntries) {
                val lower = entryName.lowercase()
                when {
                    lower.endsWith("manifest.json") -> {
                        runCatching {
                            val obj = JSONObject(content)
                            bankTitle = obj.optString("title", obj.optString("name", bankTitle)).ifBlank { bankTitle }
                            bankVersion = obj.optString("bankVersion", obj.optString("version", bankVersion)).ifBlank { bankVersion }
                        }
                    }
                    lower.endsWith("categories.json") || lower.endsWith("nodes.json") || lower.endsWith("tree.json") -> {
                        parseCategoriesJsonText(content, nodesMap)
                    }
                    lower.endsWith("lessons.json") -> {
                        parseLessonsMapJsonText(content, lessonsById)
                    }
                }
            }

            // Pass 2: bank.json, questions.json, data.json, chunks/*.json, or any remaining JSON with questions
            for ((entryName, content) in jsonEntries) {
                val lower = entryName.lowercase()
                if (lower.endsWith("manifest.json") ||
                    lower.endsWith("categories.json") ||
                    lower.endsWith("nodes.json") ||
                    lower.endsWith("tree.json") ||
                    lower.endsWith("lessons.json")
                ) {
                    continue
                }
                parseBankOrQuestionsJsonText(
                    jsonText = content,
                    nodesMap = nodesMap,
                    questions = questions,
                    crossRefs = crossRefs,
                    lessonsById = lessonsById,
                    onMeta = { title, ver ->
                        if (title.isNotBlank()) bankTitle = title
                        if (ver.isNotBlank()) bankVersion = ver
                    }
                )
            }
        } else {
            val jsonText = try {
                if (isGzip) {
                    GZIPInputStream(ByteArrayInputStream(rawBytes)).use { gis ->
                        gis.readBytes().toString(StandardCharsets.UTF_8)
                    }
                } else {
                    rawBytes.toString(StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Corrupt .medo stream: ${e.message}", e)
            }
            val trimmed = jsonText.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                throw IllegalArgumentException("Unrecognized .medo format: neither ZIP nor JSON")
            }
            parseBankOrQuestionsJsonText(
                jsonText = trimmed,
                nodesMap = nodesMap,
                questions = questions,
                crossRefs = crossRefs,
                lessonsById = lessonsById,
                onMeta = { title, ver ->
                    if (title.isNotBlank()) bankTitle = title
                    if (ver.isNotBlank()) bankVersion = ver
                }
            )
        }

        if (questions.isEmpty()) {
            throw IllegalArgumentException("Corrupt or empty .medo archive: no questions found")
        }

        // Attach external lessons if any question referenced a lesson_id in lessons.json
        val finalQuestions = if (lessonsById.isNotEmpty()) {
            questions.map { q ->
                val extLesson = lessonsById[q.siteQuestionId]
                if (q.lessonHtml.isNullOrBlank() && !extLesson.isNullOrBlank()) {
                    q.copy(lessonHtml = extLesson, hasLesson = true)
                } else {
                    q
                }
            }
        } else {
            questions
        }

        // Ensure at least one root category node exists if questions had no category metadata
        if (nodesMap.isEmpty()) {
            val defaultNodeId = "cat_imported_root"
            nodesMap[defaultNodeId] = CategoryNodeEntity(
                id = defaultNodeId,
                parentId = null,
                name = bankTitle,
                fullPath = bankTitle,
                depth = 0,
                orderIndex = 0,
                totalBankQuestionsCount = finalQuestions.size,
                isDownloaded = true
            )
            for (q in finalQuestions) {
                crossRefs.add(QuestionNodeCrossRef(siteQuestionId = q.siteQuestionId, nodeId = defaultNodeId))
            }
        } else {
            // Recompute totalBankQuestionsCount per node if 0
            val countsByNode = crossRefs.groupingBy { it.nodeId }.eachCount()
            for ((nodeId, node) in nodesMap.entries.toList()) {
                val c = countsByNode[nodeId] ?: 0
                if (node.totalBankQuestionsCount == 0 && c > 0) {
                    nodesMap[nodeId] = node.copy(totalBankQuestionsCount = c)
                }
            }
        }

        val clozeCards = finalQuestions.take(500).flatMap { q ->
            ClozeGenerator.extractClozeCardsFromLesson(
                siteQuestionId = q.siteQuestionId,
                lessonHtml = q.lessonHtml,
                categoryTitle = bankTitle,
                todayEpochDay = todayEpochDay
            )
        }

        val rootCatId = nodesMap.values.firstOrNull { it.parentId == null }?.id ?: "cat_root"

        return ParsedBankBundle(
            nodes = nodesMap.values.sortedBy { it.orderIndex },
            questions = finalQuestions,
            crossRefs = crossRefs.distinct(),
            packs = listOf(
                InstalledPackEntity(
                    packId = "pack_imported_${sha256Hex(bankTitle).take(8)}",
                    categoryId = rootCatId,
                    categoryTitle = bankTitle,
                    level = "media",
                    version = bankVersion,
                    questionCount = finalQuestions.size,
                    sizeBytes = rawBytes.size.toLong(),
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

    private fun parseCategoriesJsonText(content: String, nodesMap: MutableMap<String, CategoryNodeEntity>) {
        val trimmed = content.trim()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("categories")
                    ?: obj.optJSONArray("nodes")
                    ?: obj.optJSONArray("courses")
                    ?: obj.optJSONArray("tree")
                    ?: JSONArray()
            }
            else -> JSONArray()
        }
        parseCategoryArrayRecursive(arr, parentId = null, parentPath = "", depth = 0, nodesMap = nodesMap)
    }

    private fun parseCategoryArrayRecursive(
        arr: JSONArray,
        parentId: String?,
        parentPath: String,
        depth: Int,
        nodesMap: MutableMap<String, CategoryNodeEntity>
    ) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val rawId = o.opt("id") ?: o.opt("node_id") ?: o.opt("category_id") ?: o.opt("slug") ?: "cat_${nodesMap.size + 1}"
            val id = rawId.toString()
            val explicitParent = if (o.has("parentId") && !o.isNull("parentId")) {
                o.opt("parentId")?.toString()
            } else if (o.has("parent_id") && !o.isNull("parent_id")) {
                o.opt("parent_id")?.toString()
            } else {
                parentId
            }
            val name = o.optString("name", o.optString("title", o.optString("label", id))).ifBlank { id }
            val defaultFullPath = if (parentPath.isNotEmpty()) "$parentPath > $name" else name
            val fullPath = o.optString("fullPath", o.optString("full_path", o.optString("path", defaultFullPath))).ifBlank { defaultFullPath }
            val nodeDepth = o.optInt("depth", o.optInt("level", depth))
            val orderIdx = o.optInt("orderIndex", o.optInt("order_index", o.optInt("order", nodesMap.size + i)))
            val qCount = o.optInt(
                "totalBankQuestionsCount",
                o.optInt("question_count", o.optInt("questionCount", o.optInt("count", 0)))
            )
            val downloaded = o.optBoolean("isDownloaded", o.optBoolean("is_downloaded", true))

            nodesMap[id] = CategoryNodeEntity(
                id = id,
                parentId = explicitParent?.takeIf { it.isNotBlank() && it != "null" },
                name = name,
                fullPath = fullPath,
                depth = nodeDepth,
                orderIndex = orderIdx,
                totalBankQuestionsCount = qCount,
                isDownloaded = downloaded
            )

            val children = o.optJSONArray("children")
                ?: o.optJSONArray("subcategories")
                ?: o.optJSONArray("nodes")
                ?: o.optJSONArray("items")
            if (children != null && children.length() > 0) {
                parseCategoryArrayRecursive(children, parentId = id, parentPath = fullPath, depth = nodeDepth + 1, nodesMap = nodesMap)
            }
        }
    }

    private fun parseLessonsMapJsonText(content: String, lessonsById: MutableMap<String, String>) {
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.opt(k)
                when (v) {
                    is String -> lessonsById[k] = v
                    is JSONObject -> {
                        val html = v.optString("html", v.optString("text", v.optString("body", "")))
                        if (html.isNotBlank()) lessonsById[k] = html
                    }
                }
            }
        } else if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = (o.opt("id") ?: o.opt("question_id") ?: o.opt("lesson_id"))?.toString() ?: continue
                val html = o.optString("html", o.optString("lesson_html", o.optString("text", o.optString("body", ""))))
                if (html.isNotBlank()) lessonsById[id] = html
            }
        }
    }

    private fun parseBankOrQuestionsJsonText(
        jsonText: String,
        nodesMap: MutableMap<String, CategoryNodeEntity>,
        questions: MutableList<QuestionEntity>,
        crossRefs: MutableList<QuestionNodeCrossRef>,
        lessonsById: MutableMap<String, String>,
        onMeta: (title: String, version: String) -> Unit
    ) {
        val trimmed = jsonText.trim()
        if (trimmed.startsWith("[")) {
            parseQuestionsJsonArray(JSONArray(trimmed), nodesMap, questions, crossRefs, lessonsById)
            return
        }
        if (!trimmed.startsWith("{")) return

        val root = JSONObject(trimmed)
        val title = root.optString("title", root.optString("name", root.optString("bank_name", "")))
        val version = root.opt("version")?.toString() ?: root.optString("bankVersion", "")
        onMeta(title, version)

        // Parse embedded categories / courses / nodes if present
        val catArr = root.optJSONArray("categories")
            ?: root.optJSONArray("nodes")
            ?: root.optJSONArray("courses")
            ?: root.optJSONArray("tree")
        if (catArr != null) {
            parseCategoryArrayRecursive(catArr, parentId = null, parentPath = "", depth = 0, nodesMap = nodesMap)
        }

        // Parse embedded lessons if present
        val lessonsObj = root.optJSONObject("lessons")
        if (lessonsObj != null) {
            parseLessonsMapJsonText(lessonsObj.toString(), lessonsById)
        }
        val lessonsArr = root.optJSONArray("lessons")
        if (lessonsArr != null) {
            parseLessonsMapJsonText(lessonsArr.toString(), lessonsById)
        }

        // Parse questions array
        val qArr = root.optJSONArray("questions")
            ?: root.optJSONArray("items")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("records")
        if (qArr != null) {
            parseQuestionsJsonArray(qArr, nodesMap, questions, crossRefs, lessonsById)
        }
    }

    private fun parseQuestionsJsonArray(
        arr: JSONArray,
        nodesMap: MutableMap<String, CategoryNodeEntity>,
        questions: MutableList<QuestionEntity>,
        crossRefs: MutableList<QuestionNodeCrossRef>,
        lessonsById: Map<String, String>
    ) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val rawId = o.opt("siteQuestionId")
                ?: o.opt("site_question_id")
                ?: o.opt("question_id")
                ?: o.opt("qid")
                ?: o.opt("id")
                ?: "q_${questions.size + 1}"
            val qId = rawId.toString()

            val stemHtml = firstNonBlankOrNull(
                o,
                "stemHtml",
                "stem_html",
                "question_html",
                "questionHtml",
                "question_text",
                "questionText",
                "stem",
                "body",
                "question",
                "text",
                "content",
                "html"
            ).orEmpty()
            if (stemHtml.isBlank()) continue

            // Parse options & optional embedded votes/correctness
            val opts = mutableListOf<String>()
            val embeddedVotes = mutableListOf<Int>()
            var embeddedCorrectIdx: Int? = null

            val optsArr = o.optJSONArray("options") ?: o.optJSONArray("choices") ?: o.optJSONArray("answers")
            if (optsArr != null) {
                for (j in 0 until optsArr.length()) {
                    val elem = optsArr.opt(j)
                    when (elem) {
                        is JSONObject -> {
                            val optText = elem.optString(
                                "text",
                                elem.optString("body", elem.optString("html", elem.optString("label", elem.optString("title", ""))))
                            )
                            opts.add(optText)
                            if (elem.optBoolean("is_correct", false) || elem.optBoolean("isCorrect", false) || elem.optBoolean("correct", false)) {
                                embeddedCorrectIdx = j
                            }
                            if (elem.has("percent") || elem.has("vote_percent") || elem.has("votes")) {
                                embeddedVotes.add(elem.optInt("percent", elem.optInt("vote_percent", elem.optInt("votes", 0))))
                            }
                        }
                        else -> opts.add(elem?.toString() ?: "")
                    }
                }
            } else {
                // Fallback for flat option1..option4 or opt_a..opt_d fields
                listOf("option1", "option2", "option3", "option4", "opt_1", "opt_2", "opt_3", "opt_4", "opt_a", "opt_b", "opt_c", "opt_d").forEach { key ->
                    if (o.has(key) && !o.isNull(key)) {
                        val v = o.optString(key, "").trim()
                        if (v.isNotEmpty()) opts.add(v)
                    }
                }
            }

            // Parse option votes array
            val votesArr = o.optJSONArray("optionVotes")
                ?: o.optJSONArray("option_votes")
                ?: o.optJSONArray("percentages")
                ?: o.optJSONArray("votes")
                ?: o.optJSONArray("stats")
            val votes: List<Int>? = when {
                votesArr != null && votesArr.length() > 0 -> (0 until votesArr.length()).map { votesArr.optInt(it, 0) }
                embeddedVotes.size == opts.size && embeddedVotes.isNotEmpty() -> embeddedVotes
                else -> null
            }

            // Parse correctOptionIndex (0-based)
            val correctIdx: Int? = when {
                o.has("correctOptionIndex") && !o.isNull("correctOptionIndex") -> o.optInt("correctOptionIndex")
                o.has("correct_index") && !o.isNull("correct_index") -> o.optInt("correct_index")
                o.has("correct_answer") && !o.isNull("correct_answer") -> normalizeAnswerToZeroBased(o.opt("correct_answer"), opts.size)
                o.has("correctAnswer") && !o.isNull("correctAnswer") -> normalizeAnswerToZeroBased(o.opt("correctAnswer"), opts.size)
                o.has("correct_option") && !o.isNull("correct_option") -> normalizeAnswerToZeroBased(o.opt("correct_option"), opts.size)
                o.has("answer") && !o.isNull("answer") -> normalizeAnswerToZeroBased(o.opt("answer"), opts.size)
                o.has("correct") && !o.isNull("correct") -> normalizeAnswerToZeroBased(o.opt("correct"), opts.size)
                o.has("key") && !o.isNull("key") -> normalizeAnswerToZeroBased(o.opt("key"), opts.size)
                else -> embeddedCorrectIdx
            }

            val explanationHtml = firstNonBlankOrNull(
                o,
                "explanationHtml",
                "explanation_html",
                "answer_html",
                "answerHtml",
                "explanation",
                "answer_text",
                "descriptive_answer",
                "solution"
            )
            val lessonIdRef = o.opt("lesson_id")?.toString() ?: o.opt("lessonId")?.toString()
            val lessonHtml = firstNonBlankOrNull(
                o,
                "lessonHtml",
                "lesson_html",
                "lesson",
                "lesson_text",
                "darsnameh"
            ) ?: lessonIdRef?.let { lessonsById[it] }

            val mindMapHash = firstNonBlankOrNull(
                o,
                "mindMapMediaHash",
                "mind_map",
                "mindmap",
                "mindmap_hash",
                "mindmap_image"
            )

            // Parse or synthesize category node IDs
            val nIds = mutableListOf<String>()
            val nodeIdsArr = o.optJSONArray("nodeIds") ?: o.optJSONArray("node_ids") ?: o.optJSONArray("category_ids")
            if (nodeIdsArr != null && nodeIdsArr.length() > 0) {
                for (k in 0 until nodeIdsArr.length()) {
                    val nid = nodeIdsArr.opt(k)?.toString()?.trim().orEmpty()
                    if (nid.isNotEmpty()) nIds.add(nid)
                }
            }
            listOf("node_id", "nodeId", "category_id", "categoryId", "course_id", "courseId").forEach { key ->
                if (o.has(key) && !o.isNull(key)) {
                    val nid = o.opt(key)?.toString()?.trim().orEmpty()
                    if (nid.isNotEmpty() && nid !in nIds) nIds.add(nid)
                }
            }

            // If question specifies category/course/chapter/path names, ensure nodes exist in nodesMap
            if (nIds.isEmpty()) {
                val pathParts = mutableListOf<String>()
                val pathArr = o.optJSONArray("path") ?: o.optJSONArray("categories") ?: o.optJSONArray("hierarchy")
                if (pathArr != null && pathArr.length() > 0) {
                    for (k in 0 until pathArr.length()) {
                        val part = pathArr.optString(k, "").trim()
                        if (part.isNotEmpty()) pathParts.add(part)
                    }
                } else {
                    val rawPathStr = firstNonBlankOrNull(o, "category_path", "path", "breadcrumb")
                    if (!rawPathStr.isNullOrBlank() && (rawPathStr.contains(">") || rawPathStr.contains("/"))) {
                        rawPathStr.split(Regex("[>/]")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { pathParts.add(it) }
                    } else {
                        listOf("exam", "source", "category", "course", "subject", "chapter", "topic", "lesson_title").forEach { key ->
                            val v = firstNonBlankOrNull(o, key)
                            if (!v.isNullOrBlank() && v !in pathParts) pathParts.add(v)
                        }
                    }
                }

                if (pathParts.isNotEmpty()) {
                    var parentId: String? = null
                    var currentPath = ""
                    pathParts.forEachIndexed { depthIdx, partName ->
                        currentPath = if (currentPath.isEmpty()) partName else "$currentPath > $partName"
                        val nodeId = "cat_auto_${sha256Hex(currentPath).take(10)}"
                        if (!nodesMap.containsKey(nodeId)) {
                            nodesMap[nodeId] = CategoryNodeEntity(
                                id = nodeId,
                                parentId = parentId,
                                name = partName,
                                fullPath = currentPath,
                                depth = depthIdx,
                                orderIndex = nodesMap.size + 1,
                                totalBankQuestionsCount = 0,
                                isDownloaded = true
                            )
                        }
                        nIds.add(nodeId)
                        parentId = nodeId
                    }
                }
            }

            val (qEntity, qRefs) = createSyntheticQuestion(
                id = qId,
                nodeIds = nIds,
                stemHtml = stemHtml,
                options = opts,
                votes = votes,
                correctIndex = correctIdx,
                explanationHtml = explanationHtml,
                lessonHtml = lessonHtml,
                mindMapHash = mindMapHash,
                packId = "pack_imported_v2",
                orderInNode = o.optInt("orderInNode", o.optInt("order_in_node", o.optInt("order", questions.size + 1)))
            )
            questions.add(qEntity)
            crossRefs.addAll(qRefs)
        }
    }

    private fun normalizeAnswerToZeroBased(raw: Any?, optionCount: Int): Int? {
        if (raw == null) return null
        when (raw) {
            is Number -> {
                val n = raw.toInt()
                return when {
                    n in 0 until optionCount.coerceAtLeast(4) -> n
                    n in 1..optionCount.coerceAtLeast(4) -> n - 1
                    else -> null
                }
            }
            is String -> {
                val s = raw.trim()
                val asInt = s.toIntOrNull()
                if (asInt != null) {
                    return when {
                        asInt in 0 until optionCount.coerceAtLeast(4) -> asInt
                        asInt in 1..optionCount.coerceAtLeast(4) -> asInt - 1
                        else -> null
                    }
                }
                return when (s.lowercase()) {
                    "a", "الف", "۱", "1" -> 0
                    "b", "ب", "۲", "2" -> 1
                    "c", "ج", "۳", "3" -> 2
                    "d", "د", "۴", "4" -> 3
                    else -> null
                }
            }
            else -> return null
        }
    }

    private fun firstNonBlankOrNull(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val v = obj.optString(k, "").trim()
                if (v.isNotEmpty() && v != "null") return v
            }
        }
        return null
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

    /**
     * Inserts a 20,000-question synthetic bank into a real Room SQLite database in streaming batches
     * of 2,000 questions so heap memory stays well under 150 MB.
     */
    suspend fun import20kSyntheticBankIntoSqlite(
        dao: MedoDao,
        questionCount: Int = 20_000
    ): Long {
        val startMs = System.currentTimeMillis()
        val benchNodes = (1..20).map { idx ->
            CategoryNodeEntity(
                id = "cat_bench_$idx",
                parentId = if (idx > 5) "cat_bench_${(idx % 5) + 1}" else null,
                name = "$DEMO_LABEL مبحث بنچمارک $idx",
                fullPath = "$DEMO_LABEL مبحث بنچمارک $idx",
                depth = if (idx > 5) 1 else 0,
                orderIndex = idx,
                totalBankQuestionsCount = questionCount / 20,
                isDownloaded = true
            )
        }
        dao.insertCategoryNodes(benchNodes)

        val optionsJson = "[\"گزینه الف آزمایشی\",\"گزینه ب آزمایشی\",\"گزینه ج آزمایشی\",\"گزینه د آزمایشی\"]"
        val batchSize = 2_000
        var startIdx = 1
        while (startIdx <= questionCount) {
            val endIdx = (startIdx + batchSize - 1).coerceAtMost(questionCount)
            val qBatch = ArrayList<QuestionEntity>(endIdx - startIdx + 1)
            val refBatch = ArrayList<QuestionNodeCrossRef>(endIdx - startIdx + 1)
            for (i in startIdx..endIdx) {
                val qId = "q_bench_$i"
                val nodeId = "cat_bench_${(i % 20) + 1}"
                val norm = "نمونه ازمایشی سوال سنتتیک شماره $i برای بنچمارک ظرفیت 20 هزار سوالی" +
                    if (i % 500 == 0) " کلیدواژه ویژه بنچمارک" else ""
                val hasLessonFlag = (i == 1 || i % 3 == 0)
                val lesson = if (hasLessonFlag) {
                    "<h2>$DEMO_LABEL درسنامه جامع آزمایشی سوال $i</h2><p>متن درسنامه ذخیره شده در SQLite که به صورت Lazy برای هر سوال بارگذاری می‌شود.</p>"
                } else {
                    null
                }
                qBatch.add(
                    QuestionEntity(
                        siteQuestionId = qId,
                        contentHash = "hash_$i",
                        stemHtml = "<p>$DEMO_LABEL سوال سنتتیک شماره $i برای بنچمارک ۲۰ هزار سوالی</p>",
                        optionsJson = optionsJson,
                        optionVotesJson = if (i % 10 == 0) "[15,55,20,10]" else null,
                        correctOptionIndex = i % 4,
                        explanationHtml = if (hasLessonFlag) "<p>تشریح آزمایشی $i</p>" else null,
                        lessonHtml = lesson,
                        mindMapMediaHash = null,
                        stemImageMediaHash = null,
                        drugSafetyMetaJson = null,
                        normalizedSearchText = norm,
                        hasKey = true,
                        hasLesson = hasLessonFlag,
                        hasMindMap = false,
                        isTrap = (i % 10 == 0),
                        removedUpstream = false,
                        packId = "pack_bench",
                        orderInNode = i
                    )
                )
                refBatch.add(QuestionNodeCrossRef(siteQuestionId = qId, nodeId = nodeId))
            }
            dao.insertQuestions(qBatch)
            dao.insertQuestionNodeCrossRefs(refBatch)
            startIdx = endIdx + 1
        }
        return (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
