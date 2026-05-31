package com.memorychat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MemoryChatSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsConversationList() {
        compose.onNodeWithText("MemoryChat").assertIsDisplayed()
    }
}
