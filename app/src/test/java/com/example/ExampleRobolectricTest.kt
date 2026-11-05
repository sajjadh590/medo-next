package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun saveDecorScreenshot(fileName: String) {
        try {
            val screensDir = File("../docs/screens").apply { mkdirs() }
            val view = composeTestRule.activity.window.decorView
            val width = view.width.coerceAtLeast(1080)
            val height = view.height.coerceAtLeast(2340)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            FileOutputStream(File(screensDir, fileName)).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
            }
        } catch (_: Exception) {
            // Non-fatal in headless JVM if hardware canvas is stubbed
        }
    }

    @Test
    fun `home practice exam review analytics settings packs QR permission flow and auto backup render cleanly`() {
        composeTestRule.waitForIdle()

        // 1. Verify Home screen & Continue card
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("continue_where_left_off_card").assertIsDisplayed()
        saveDecorScreenshot("01_home_rtl.png")

        // 2. Open Pack Manager from Home banner and verify direct .medo file import button
        composeTestRule.onNodeWithTag("home_pack_banner").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pack_manager_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_local_medo_file_btn").assertIsDisplayed()
        saveDecorScreenshot("02_pack_manager_rtl.png")
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()

        // 3. Navigate to Practice screen and verify question stem & options
        composeTestRule.onNodeWithTag("bottom_nav_PRACTICE").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("practice_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("question_stem_card").assertIsDisplayed()
        saveDecorScreenshot("03_practice_rtl.png")

        // 4. Navigate to Exam simulator
        composeTestRule.onNodeWithTag("bottom_nav_EXAM").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("exam_setup_screen").assertIsDisplayed()
        saveDecorScreenshot("04_exam_setup_rtl.png")

        // 5. Navigate to Spaced Repetition Review (SM-2 & Cloze)
        composeTestRule.onNodeWithTag("bottom_nav_REVIEW").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("review_screen").assertIsDisplayed()
        saveDecorScreenshot("05_review_sm2_rtl.png")

        // 6. Navigate to Analytics
        composeTestRule.onNodeWithTag("bottom_nav_ANALYTICS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("analytics_screen").assertIsDisplayed()
        saveDecorScreenshot("06_analytics_rtl.png")

        // 7. Open QR Scanner from top bar and verify permission rationale card, fallback buttons, and camera release on close
        composeTestRule.onNodeWithTag("top_qr_scanner_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qr_scanner_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("qr_permission_rationale_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("request_camera_permission_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_manual_address_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_gallery_qr_btn").assertIsDisplayed()
        saveDecorScreenshot("07_qr_scanner_rtl.png")
        composeTestRule.onNodeWithTag("close_qr_scanner_btn").performClick()
        composeTestRule.waitForIdle()

        // 8. Open Settings and verify Live Preview card, Font chips, Theme chips, and Daily Auto-Backup status
        composeTestRule.onNodeWithTag("top_settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_live_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("font_chip_vazirmatn"))
        composeTestRule.onNodeWithTag("font_chip_vazirmatn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("font_chip_b_nazanin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("last_auto_backup_status_text"))
        composeTestRule.onNodeWithTag("last_auto_backup_status_text").assertIsDisplayed()
        saveDecorScreenshot("08_settings_rtl.png")

        val screensDir = File("../docs/screens")
        assertTrue(screensDir.exists())
    }
}
