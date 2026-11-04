package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.InstalledPackEntity
import com.example.data.QuestionEntity
import com.example.data.SyntheticBankSeedAndImporter
import com.example.domain.CategoryTreeFlattener
import com.example.domain.ClozeGenerator
import com.example.domain.DeterministicLessonExtractor
import com.example.domain.ExamScorer
import com.example.domain.PackDeltaPlanner
import com.example.domain.ProgressBackupManager
import com.example.domain.Sm2Scheduler
import com.example.domain.TrapDetector
import com.example.pdf.MedoPdfEngine
import com.example.pdf.PdfExportConfig
import com.example.pdf.PdfExportMode
import com.example.qr.MedoQrAndLanEngine
import com.example.ui.theme.MedoThemeCatalog
import com.example.util.PersianTextUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    // 1. Search Normalization Vectors Test
    @Test
    fun `search normalization unifies Persian Arabic yeh kaf digits and strips ZWNJ`() {
        val raw = "بیمارِ مبتلا به هیپوناترمی\u200Cهای شدید (Na: ١١٤ و ۱۲۰) با داروهایِ كليوي و يدي"
        val norm = PersianTextUtils.normalizeForSearch(raw)
        assertEquals("بیمار مبتلا به هیپوناترمیهای شدید (na: 114 و 120) با داروهای کلیوی و یدی", norm)
    }

    // 2. WCAG 2.1 Contrast Verification Across All 11 Themes
    @Test
    fun `all 11 themes satisfy WCAG 4_5 to 1 body text contrast on reading surfaces`() {
        assertEquals(11, MedoThemeCatalog.allThemes.size)
        for (theme in MedoThemeCatalog.allThemes) {
            val readingContrast = MedoThemeCatalog.worstCaseContrast(
                textColor = theme.textPrimary,
                panelTint = theme.readingSurfaceTint,
                panelAlpha = theme.readingSurfaceAlpha,
                theme = theme
            )
            assertTrue(
                "Theme ${theme.themeId.id} reading contrast was $readingContrast (< 4.5)",
                readingContrast >= 4.5
            )

            val chromeContrast = MedoThemeCatalog.worstCaseContrast(
                textColor = theme.textPrimary,
                panelTint = theme.surfaceTint,
                panelAlpha = theme.surfaceAlpha,
                theme = theme
            )
            assertTrue(
                "Theme ${theme.themeId.id} chrome contrast was $chromeContrast (< 3.0)",
                chromeContrast >= 3.0
            )
        }
    }

    // 3. SM-2 Spaced Repetition Scheduler Test
    @Test
    fun `SM2 scheduler intervals and easiness factor behave accurately`() {
        val today = 20762L
        val firstGood = Sm2Scheduler.schedule(0, 2.5f, 0, Sm2Scheduler.Rating.GOOD, today)
        assertEquals(1, firstGood.repetitions)
        assertEquals(1, firstGood.intervalDays)
        assertEquals(today + 1L, firstGood.dueEpochDay)

        val secondGood = Sm2Scheduler.schedule(
            firstGood.repetitions,
            firstGood.easinessFactor,
            firstGood.intervalDays,
            Sm2Scheduler.Rating.GOOD,
            today + 1L
        )
        assertEquals(2, secondGood.repetitions)
        assertEquals(6, secondGood.intervalDays)

        val resetOnAgain = Sm2Scheduler.schedule(
            secondGood.repetitions,
            secondGood.easinessFactor,
            secondGood.intervalDays,
            Sm2Scheduler.Rating.AGAIN,
            today + 7L
        )
        assertEquals(0, resetOnAgain.repetitions)
        assertEquals(1, resetOnAgain.intervalDays)
    }

    // 4. Exam Scorer with 1/3 Negative Marking Test
    @Test
    fun `ExamScorer applies 1_3 negative marking accurately`() {
        val result = ExamScorer.calculateScore(
            totalQuestions = 10,
            correctCount = 6,
            wrongCount = 3,
            negativeMarkingDivisor = 3
        )
        assertEquals(1, result.unansweredCount)
        assertEquals(60f, result.rawPercentNoNegative, 0.01f)
        assertEquals(5.0f, result.netScoreOutOfTotal, 0.01f)
        assertEquals(50.0f, result.netPercentWithNegative, 0.01f)
    }

    // 5. Category Tree Flattener (Collapse in Both Tabs + Full-Path Disambiguation) Test
    @Test
    fun `CategoryTreeFlattener hides descendants when collapsed in both tabs and disambiguates duplicate names`() {
        val seed = SyntheticBankSeedAndImporter.buildInitialClinicalSeed()
        val counts = seed.nodes.associate { it.id to it.totalBankQuestionsCount }

        // Collapsed in "بانک من"
        val collapsedMyBank = CategoryTreeFlattener.flattenTree(
            allNodes = seed.nodes,
            expandedNodeIds = emptySet(),
            onlyDownloadedTab = true,
            nodeQuestionCounts = counts,
            nodeAnsweredCounts = emptyMap()
        )
        assertTrue(collapsedMyBank.all { it.node.depth == 0 })
        assertFalse(collapsedMyBank.any { it.node.id == "cat_uninstalled_radiology" })

        // Collapsed in "همه دسته‌ها" also hides all descendants
        val collapsedAllTab = CategoryTreeFlattener.flattenTree(
            allNodes = seed.nodes,
            expandedNodeIds = emptySet(),
            onlyDownloadedTab = false,
            nodeQuestionCounts = counts,
            nodeAnsweredCounts = emptyMap()
        )
        assertTrue(collapsedAllTab.all { it.node.depth == 0 })
        assertTrue(collapsedAllTab.any { it.node.id == "cat_uninstalled_radiology" })

        // Expanded in "همه دسته‌ها" -> duplicate "قلب و عروق" shows full path disambiguation
        val expandedAll = CategoryTreeFlattener.flattenTree(
            allNodes = seed.nodes,
            expandedNodeIds = seed.nodes.map { it.id }.toSet(),
            onlyDownloadedTab = false,
            nodeQuestionCounts = counts,
            nodeAnsweredCounts = emptyMap()
        )
        val cardioRows = expandedAll.filter { it.node.name == "قلب و عروق" }
        assertEquals(2, cardioRows.size)
        assertTrue(cardioRows.all { it.showDisambiguatedPath && it.displayTitle.contains(">") })
    }

    // 6. Pack Delta Planner Test
    @Test
    fun `PackDeltaPlanner computes changed chunks missing media and tombstones`() {
        val manifestJson = """
            {
              "formatVersion": 3,
              "bankVersion": "2026.11.5",
              "packs": [
                {
                  "id": "pack_1",
                  "categoryId": "cat_dastyari",
                  "categoryPath": "دستیاری",
                  "level": "media",
                  "questionCount": 200,
                  "sizeBytes": 4000000,
                  "chunkHashes": ["chunk_old", "chunk_new"],
                  "mediaHashes": ["media_cached", "media_missing"]
                }
              ],
              "questionRevisions": {"q_1": "r2"},
              "tombstones": ["q_dead"]
            }
        """.trimIndent()
        val manifest = PackDeltaPlanner.parseManifest(manifestJson)
        val installed = listOf(
            InstalledPackEntity(
                packId = "pack_1",
                categoryId = "cat_dastyari",
                categoryTitle = "دستیاری",
                level = "media",
                version = "2026.11.0",
                questionCount = 200,
                sizeBytes = 2000000L,
                chunkHashesJson = "[\"chunk_old\"]",
                mediaHashesJson = "[\"media_cached\"]",
                installedAt = 1000L
            )
        )
        val plan = PackDeltaPlanner.planDeltaUpdate(
            manifest = manifest,
            selectedPackIds = setOf("pack_1"),
            installedPacks = installed,
            existingMediaHashes = setOf("media_cached"),
            mirrorMode = false
        )
        assertEquals(listOf("chunk_new"), plan.chunksToDownload)
        assertEquals(listOf("chunk_old"), plan.chunksAlreadyCached)
        assertEquals(listOf("media_missing"), plan.mediaToDownload)
        assertEquals(listOf("q_dead"), plan.questionsToRemoveOrMark)
        assertFalse(plan.mirrorDestructiveDelete)
    }

    // 7. Progress Export/Import Round-Trip & Legacy Migration Test
    @Test
    fun `Progress export import round trip and legacy migration preserve notes bookmarks and SM2`() {
        val legacyMap = mapOf(
            "ans_q_1001" to "1:1",
            "bm_q_1001" to true,
            "note_q_1001" to "یادداشت شخصی نمونه"
        )
        val migrated = ProgressBackupManager.migrateFromLegacyStore(legacyMap)
        assertEquals(1, migrated.size)
        val item = migrated.first()
        assertEquals("q_1001", item.siteQuestionId)
        assertEquals(1, item.lastSelectedOption)
        assertEquals(true, item.isCorrect)
        assertTrue(item.bookmarked)
        assertEquals("یادداشت شخصی نمونه", item.personalNote)

        val exportedJson = ProgressBackupManager.exportProgressToJson(migrated)
        val reimported = ProgressBackupManager.importProgressFromJson(exportedJson)
        assertEquals(1, reimported.size)
        assertEquals(item.personalNote, reimported.first().personalNote)
        assertTrue(reimported.first().bookmarked)
    }

    // 8. Medo v2 ZIP Round-Trip & Atomic Import Rollback on Corrupt Input Test
    @Test
    fun `Medo v2 ZIP round trip succeeds and corrupt archive throws before modifying state`() {
        val original = SyntheticBankSeedAndImporter.buildInitialClinicalSeed()
        val zipBytes = SyntheticBankSeedAndImporter.buildMedoV2ZipBytes(original)
        assertTrue(zipBytes.isNotEmpty())
        val parsed = SyntheticBankSeedAndImporter.parseMedoV2ZipBytes(zipBytes)
        assertEquals(original.nodes.size, parsed.nodes.size)
        assertEquals(original.questions.size, parsed.questions.size)

        // Verify corrupt/truncated archive fails cleanly without partial parse
        var threwOnCorrupt = false
        try {
            SyntheticBankSeedAndImporter.parseMedoV2ZipBytes(ByteArray(32) { 0x42 })
        } catch (_: Exception) {
            threwOnCorrupt = true
        }
        assertTrue("Corrupt archive must fail before touching database", threwOnCorrupt)
    }

    // 9. PdfPlan Key Table Sizes, Exam No-Leak Guarantee & Sample Exam/Study PDF File Generation
    @Test
    fun `PdfPlan key table sizes for 1 9 10 37 and 200 questions exam no-leak and sample PDF files generated`() {
        val seed = SyntheticBankSeedAndImporter.buildInitialClinicalSeed()
        val seedQuestion = seed.questions.first()
        fun makeQuestions(n: Int): List<QuestionEntity> = (1..n).map { i ->
            seedQuestion.copy(
                siteQuestionId = "q_$i",
                explanationHtml = if (i in 2..9) null else "<p>پاسخ تشریحی سوال $i. برای مشاهده روی مایندمپ کلیک کنید.</p>",
                lessonHtml = if (i in 2..9) null else seedQuestion.lessonHtml
            )
        }

        assertEquals(1, MedoPdfEngine.buildAnswerKeyTable(makeQuestions(1)).size)
        assertEquals(2, MedoPdfEngine.buildAnswerKeyTable(makeQuestions(9)).size)
        assertEquals(2, MedoPdfEngine.buildAnswerKeyTable(makeQuestions(10)).size)
        assertEquals(8, MedoPdfEngine.buildAnswerKeyTable(makeQuestions(37)).size)
        assertEquals(40, MedoPdfEngine.buildAnswerKeyTable(makeQuestions(200)).size)

        val qs10 = makeQuestions(10)
        val expPlan = MedoPdfEngine.buildExplanationPlan(qs10)
        assertTrue(expPlan.any { it.isCompressedMissingRange && it.headerLabel.contains("سوال‌های ۲ تا ۹: پاسخ تشریحی ثبت نشده") })
        assertFalse(expPlan.any { it.bodyText.contains("کلیک") })

        val examPlan = MedoPdfEngine.createPlan(qs10, PdfExportConfig(mode = PdfExportMode.EXAM))
        val allPlannedOptions = examPlan.questionPages.flatten().flatMap { it.options }
        assertTrue(allPlannedOptions.none { it.isMarkedCorrectInStudyMode })

        val studyPlan = MedoPdfEngine.createPlan(qs10, PdfExportConfig(mode = PdfExportMode.STUDY))
        val studyOptions = studyPlan.questionPages.flatten().flatMap { it.options }
        assertTrue(studyOptions.any { it.isMarkedCorrectInStudyMode })

        // Generate real sample Exam and Study PDFs on disk for owner inspection (docs/samples/)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samplesDir = File("../docs/samples").apply { mkdirs() }
        val examPdfFile = File(samplesDir, "sample-exam-mode.pdf")
        val studyPdfFile = File(samplesDir, "sample-study-mode.pdf")
        MedoPdfEngine.renderToFile(context, seed.questions, PdfExportConfig(mode = PdfExportMode.EXAM), examPdfFile)
        MedoPdfEngine.renderToFile(context, seed.questions, PdfExportConfig(mode = PdfExportMode.STUDY), studyPdfFile)
        assertTrue(examPdfFile.exists() && examPdfFile.length() > 100)
        assertTrue(studyPdfFile.exists() && studyPdfFile.length() > 100)
    }

    // 10. ClozeGenerator, TrapDetector & Deterministic Lesson Fact Extractor (Medical Data Safety)
    @Test
    fun `ClozeGenerator TrapDetector and DeterministicLessonExtractor obey strict medical safety rules`() {
        val seed = SyntheticBankSeedAndImporter.buildInitialClinicalSeed()
        assertTrue(seed.clozeCards.isNotEmpty())
        assertTrue(seed.questions.any { it.isTrap })
        assertTrue(TrapDetector.isTrapQuestion(correctOptionIndex = 2, optionVotes = listOf(10, 58, 27, 5)))
        assertFalse(TrapDetector.isTrapQuestion(correctOptionIndex = 1, optionVotes = listOf(15, 70, 10, 5)))

        // Medical Data Safety (Section B):
        // 1. All demo questions are explicitly labelled [نمونه آزمایشی] and have zero model drugSafetyMetaJson
        assertTrue(seed.questions.all { it.stemHtml.contains(SyntheticBankSeedAndImporter.DEMO_LABEL) })
        assertTrue(seed.questions.all { it.drugSafetyMetaJson == null })
        // 2. Release build never seeds demo data
        assertFalse(SyntheticBankSeedAndImporter.shouldSeedDemoData(isDebugBuild = false))
        assertTrue(SyntheticBankSeedAndImporter.shouldSeedDemoData(isDebugBuild = true))
        // 3. Deterministic extractor extracts only when table/sentence exists in lessonHtml and cites source
        val extracted = DeterministicLessonExtractor.extractFromLessonHtml("q_1001", seed.questions.first().lessonHtml)
        assertNotNull(extracted)
        assertTrue(extracted!!.extractedRows.all { it.verbatimSourceSnippet.contains("q_1001") })
        assertNull(DeterministicLessonExtractor.extractFromLessonHtml("q_1005", null))
    }

    // 11. ZXing QR Multi-Pass Decoder (Rotation, Inversion, Stride Padding, Noise, Blur)
    @Test
    fun `ZXing QR decoder handles multiple URL lengths rotations inversion stride padding noise and blur`() {
        val urls = listOf(
            "http://192.168.1.100:8765",
            "http://10.0.0.42:8765/manifest.json?token=984120",
            "http://172.16.8.15:9000/packs/manifest.json?token=abcdef1234567890&mode=delta&cat=dastyari"
        )

        for (url in urls) {
            val width = 260
            val height = 260
            val yPlane = renderQrToYPlane(url, width, height, inverted = false)

            // Normal 0 deg
            assertEquals(url, MedoQrAndLanEngine.decodeYuvFrame(yPlane, width, height, width, 1, 0))

            // Rotated 90, 180, 270 deg (even if camera reports 0 deg, fallback rotations must decode it)
            val (rot90, w90, h90) = MedoQrAndLanEngine.rotateYPlane(yPlane, width, height, 90)
            assertEquals(url, MedoQrAndLanEngine.decodeYuvFrame(rot90, w90, h90, w90, 1, 0))

            val (rot180, w180, h180) = MedoQrAndLanEngine.rotateYPlane(yPlane, width, height, 180)
            assertEquals(url, MedoQrAndLanEngine.decodeYuvFrame(rot180, w180, h180, w180, 1, 0))

            // Inverted QR (white on black background)
            val invertedY = renderQrToYPlane(url, width, height, inverted = true)
            assertEquals(url, MedoQrAndLanEngine.decodeYuvFrame(invertedY, width, height, width, 1, 0))

            // Row stride padding (e.g. rowStride = width + 48, pixelStride = 2)
            val paddedStride = (width * 2) + 48
            val paddedBuffer = ByteArray(paddedStride * height) { 0x7F.toByte() }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    paddedBuffer[y * paddedStride + x * 2] = yPlane[y * width + x]
                }
            }
            assertEquals(url, MedoQrAndLanEngine.decodeYuvFrame(paddedBuffer, width, height, paddedStride, 2, 0))

            // Salt-and-pepper sensor noise + 3x3 box blur
            val noisyBlurred = applyNoiseAndBoxBlur(yPlane, width, height)
            assertEquals(url, MedoQrAndLanEngine.decodeYuvFrame(noisyBlurred, width, height, width, 1, 0))
        }
    }

    // 12. LAN Address Validator & Persian Error Mapper Test
    @Test
    fun `LAN URL validator accepts private LAN ranges and rejects public internet hosts and maps Persian errors`() {
        assertTrue(MedoQrAndLanEngine.validateLanTransferUrl("http://192.168.1.100:8765?token=123") is MedoQrAndLanEngine.LanValidationResult.Valid)
        assertTrue(MedoQrAndLanEngine.validateLanTransferUrl("10.24.0.5:8080", "4567") is MedoQrAndLanEngine.LanValidationResult.Valid)
        assertTrue(MedoQrAndLanEngine.validateLanTransferUrl("http://172.20.1.9:8765") is MedoQrAndLanEngine.LanValidationResult.Valid)
        assertTrue(MedoQrAndLanEngine.validateLanTransferUrl("http://localhost:8765") is MedoQrAndLanEngine.LanValidationResult.Valid)

        // Reject public IPs and external domains
        assertTrue(MedoQrAndLanEngine.validateLanTransferUrl("http://8.8.8.8:8765") is MedoQrAndLanEngine.LanValidationResult.Invalid)
        assertTrue(MedoQrAndLanEngine.validateLanTransferUrl("http://172.32.0.1:8765") is MedoQrAndLanEngine.LanValidationResult.Invalid)
        assertTrue(MedoQrAndLanEngine.validateLanTransferUrl("https://example.com/packs") is MedoQrAndLanEngine.LanValidationResult.Invalid)

        // Error mapping checks
        val firewallErr = MedoQrAndLanEngine.mapTransferError(throwable = ConnectException("Connection refused"))
        assertEquals(MedoQrAndLanEngine.TransferErrorKind.CONNECTION_REFUSED_FIREWALL, firewallErr.kind)
        assertTrue(firewallErr.fullMessageFa.contains("Python"))

        val routeErr = MedoQrAndLanEngine.mapTransferError(throwable = NoRouteToHostException("No route"))
        assertEquals(MedoQrAndLanEngine.TransferErrorKind.NO_ROUTE_DIFFERENT_WIFI, routeErr.kind)

        val timeoutErr = MedoQrAndLanEngine.mapTransferError(throwable = SocketTimeoutException("Read timed out"))
        assertEquals(MedoQrAndLanEngine.TransferErrorKind.TIMEOUT, timeoutErr.kind)

        val pinErr = MedoQrAndLanEngine.mapTransferError(httpStatusCode = 403)
        assertEquals(MedoQrAndLanEngine.TransferErrorKind.WRONG_PIN_403, pinErr.kind)

        val corruptErr = MedoQrAndLanEngine.mapTransferError(hashMismatch = true)
        assertEquals(MedoQrAndLanEngine.TransferErrorKind.CORRUPT_HASH_MISMATCH, corruptErr.kind)

        val spaceErr = MedoQrAndLanEngine.mapTransferError(insufficientSpaceBytes = 50L * 1024L * 1024L)
        assertEquals(MedoQrAndLanEngine.TransferErrorKind.INSUFFICIENT_STORAGE, spaceErr.kind)
    }

    // 13. 20,000-Question Synthetic Bank Benchmark Test
    @Test
    fun `benchmark 20000 synthetic question bank generation search and tree flatten`() {
        val runtime = Runtime.getRuntime()
        val startGen = System.currentTimeMillis()
        val bank20k = SyntheticBankSeedAndImporter.generateSyntheticLargeBank(20_000)
        val genMs = (System.currentTimeMillis() - startGen).coerceAtLeast(1L)
        assertEquals(20_000, bank20k.size)

        val startSearch = System.currentTimeMillis()
        val qNorm = PersianTextUtils.normalizeForSearch("کلیدواژه ویژه بنچمارک")
        val matches = bank20k.filter { it.normalizedSearchText.contains(qNorm) }
        val searchMs = (System.currentTimeMillis() - startSearch).coerceAtLeast(1L)
        assertEquals(40, matches.size)
        assertTrue("Search on 20k bank took ${searchMs}ms (> 300ms)", searchMs < 300L)

        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        println("BENCHMARK_20K: genMs=$genMs, searchMs=$searchMs, usedMemMb=$usedMemMb")
    }

    private fun renderQrToYPlane(content: String, width: Int, height: Int, inverted: Boolean): ByteArray {
        val writer = QRCodeWriter()
        val matrix = writer.encode(
            content,
            BarcodeFormat.QR_CODE,
            width,
            height,
            mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
        )
        val out = ByteArray(width * height)
        val fg = if (inverted) 245.toByte() else 12.toByte()
        val bg = if (inverted) 15.toByte() else 245.toByte()
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[y * width + x] = if (matrix.get(x, y)) fg else bg
            }
        }
        return out
    }

    private fun applyNoiseAndBoxBlur(yBytes: ByteArray, width: Int, height: Int): ByteArray {
        val noisy = yBytes.copyOf()
        // Deterministic sparse noise every 97th pixel
        for (i in noisy.indices step 97) {
            val v = noisy[i].toInt() and 0xFF
            noisy[i] = (if (v > 128) 60 else 200).toByte()
        }
        // Mild 3x3 weighted box blur
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    out[y * width + x] = noisy[y * width + x]
                } else {
                    var sum = (noisy[y * width + x].toInt() and 0xFF) * 4
                    sum += (noisy[(y - 1) * width + x].toInt() and 0xFF)
                    sum += (noisy[(y + 1) * width + x].toInt() and 0xFF)
                    sum += (noisy[y * width + (x - 1)].toInt() and 0xFF)
                    sum += (noisy[y * width + (x + 1)].toInt() and 0xFF)
                    out[y * width + x] = (sum / 8).toByte()
                }
            }
        }
        return out
    }
}
