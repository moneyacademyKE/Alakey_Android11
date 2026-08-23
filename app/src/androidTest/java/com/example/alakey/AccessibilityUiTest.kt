package com.example.alakey

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

class AccessibilityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComposeTestActivity>()

    @Test fun dockDestinationsExposeNamedActions() {
        listOf("Library", "Inbox", "Marketplace", "Queue").forEach { label ->
            compose.onNodeWithContentDescription(label).assertHasClickAction()
        }
    }
}
