package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class ComposeCrashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testShareApk() {
        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                com.example.ui.navigation.AppNavigation()
            }
        }
        
        composeTestRule.waitForIdle()
        try {
            composeTestRule.onNodeWithContentDescription("Menu", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
        } catch(e: Exception) {}
        
        try {
            composeTestRule.onNodeWithText("Share App APK", substring = true, useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
        } catch(e: Exception) {}
    }
}
