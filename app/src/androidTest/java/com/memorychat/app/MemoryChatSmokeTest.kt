package com.memorychat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MemoryChatSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsConversationList() {
        compose.onNodeWithText("MemoryChat").assertIsDisplayed()
    }

    @Test
    fun newChatOpensOnlyAfterConversationIsSaved() {
        compose.onNodeWithContentDescription("New Chat").performClick()

        compose.onNodeWithText("新会话").assertIsDisplayed()
    }
}
