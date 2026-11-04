package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `home practice exam review analytics settings and QR permission flow render cleanly`() {
        composeTestRule.waitForIdle()

        // 1. Verify Home screen & Continue card
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("continue_where_left_off_card").assertIsDisplayed()

        // 2. Navigate to Practice screen and verify question stem & options
        composeTestRule.onNodeWithTag("bottom_nav_PRACTICE").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("practice_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("question_stem_card").assertIsDisplayed()

        // 3. Navigate to Exam simulator
        composeTestRule.onNodeWithTag("bottom_nav_EXAM").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("exam_setup_screen").assertIsDisplayed()

        // 4. Navigate to Spaced Repetition Review (SM-2 & Cloze)
        composeTestRule.onNodeWithTag("bottom_nav_REVIEW").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("review_screen").assertIsDisplayed()

        // 5. Navigate to Analytics
        composeTestRule.onNodeWithTag("bottom_nav_ANALYTICS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("analytics_screen").assertIsDisplayed()

        // 6. Open QR Scanner from top bar and verify permission rationale card & fallback buttons
        composeTestRule.onNodeWithTag("top_qr_scanner_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qr_scanner_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("qr_permission_rationale_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("request_camera_permission_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_manual_address_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_gallery_qr_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_qr_scanner_btn").performClick()
        composeTestRule.waitForIdle()

        // 7. Open Settings and verify Live Preview card, Font chips, and Theme chips
        composeTestRule.onNodeWithTag("top_settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_live_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("font_chip_vazirmatn"))
        composeTestRule.onNodeWithTag("font_chip_vazirmatn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("font_chip_b_nazanin").assertIsDisplayed()
    }
}

