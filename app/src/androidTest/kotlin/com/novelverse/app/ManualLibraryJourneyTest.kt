package com.novelverse.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ManualLibraryJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun addNovelAndRestoreAfterActivityRecreation() {
        val title = "My local novel ${System.nanoTime()}"
        compose.onNodeWithContentDescription("Add novel").performClick()
        compose.onNodeWithTag("novel_title").performTextInput(title)
        compose.onNodeWithTag("save_novel").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Novel details").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Completed", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Remove from Library").performScrollTo().performClick()
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Restore to Library").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Restore to Library").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Remove from Library").fetchSemanticsNodes().isNotEmpty() }
    }
}
