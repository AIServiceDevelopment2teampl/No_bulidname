package com.team12.closet

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.MainTab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClosetAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startsOnHomeAndShowsFourTabs() {
        composeRule.setContent { ClosetApp() }

        MainTab.entries.forEach { composeRule.onNodeWithTag("tab_${it.name}").assertIsDisplayed() }
        composeRule.onNodeWithTag("screen_HOME").assertIsDisplayed()
    }

    @Test
    fun tappingTabSwitchesScreen() {
        composeRule.setContent { ClosetApp() }

        composeRule.onNodeWithTag("tab_CLOSET").performClick()

        composeRule.onNodeWithTag("screen_CLOSET").assertIsDisplayed()
    }

    // release/play-store-release.md 완료 조건: targetSdk 36 이상
    @Test
    fun targetSdkIsAtLeast36() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(context.applicationInfo.targetSdkVersion >= 36)
    }
}
