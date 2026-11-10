package com.team12.closet

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.MainTab
import com.team12.closet.ui.theme.AppTheme
import com.team12.closet.ui.theme.DarkColors
import com.team12.closet.ui.theme.InMemoryThemeStore
import com.team12.closet.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ClosetAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startsOnHomeAndShowsFourTabs() {
        composeRule.setContent {
            ClosetApp(FakeClothesRepository(), InMemoryThemeStore(), FakeAuthRepository(), FakeGoogleSignIn(), FakeShopsRepository())
        }

        MainTab.entries.forEach { composeRule.onNodeWithTag("tab_${it.name}").assertIsDisplayed() }
        composeRule.onNodeWithTag("screen_HOME").assertIsDisplayed()
    }

    @Test
    fun tappingTabSwitchesScreen() {
        composeRule.setContent {
            ClosetApp(FakeClothesRepository(), InMemoryThemeStore(), FakeAuthRepository(), FakeGoogleSignIn(), FakeShopsRepository())
        }

        composeRule.onNodeWithTag("tab_CLOSET").performClick()

        composeRule.onNodeWithTag("screen_CLOSET").assertIsDisplayed()
    }

    @Test
    fun closetTabOpensPhotoRegisterScreen() {
        composeRule.setContent {
            ClosetApp(FakeClothesRepository(), InMemoryThemeStore(), FakeAuthRepository(), FakeGoogleSignIn(), FakeShopsRepository())
        }
        composeRule.onNodeWithTag("tab_CLOSET").performClick()

        composeRule.onNodeWithTag("open_register_photo").performClick()

        composeRule.onNodeWithTag("pick_photos").assertIsDisplayed()
    }

    // release/play-store-release.md 완료 조건: targetSdk 36 이상
    @Test
    fun targetSdkIsAtLeast36() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(context.applicationInfo.targetSdkVersion >= 36)
    }

    // README 'AI에게 요청': 하단 탭은 홈 / 내 옷장 / 쇼핑 / 마이
    @Test
    fun tabLabelsMatchSitemap() {
        composeRule.setContent {
            ClosetApp(FakeClothesRepository(), InMemoryThemeStore(), FakeAuthRepository(), FakeGoogleSignIn(), FakeShopsRepository())
        }

        // 홈 화면에도 '내 옷장' 글자가 있어서 탭 안에서만 찾는다
        mapOf("HOME" to "홈", "CLOSET" to "내 옷장", "SHOPPING" to "쇼핑", "MY" to "마이").forEach { (tab, label) ->
            composeRule.onNode(hasTestTag("tab_$tab") and hasAnyDescendant(hasText(label)), useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithText("번역").assertDoesNotExist()
    }

    // 색 값은 docs/app-sitemap/index.html 시안과 같다
    @Test
    fun themeColorsMatchMockup() {
        assertEquals(Color(0xFF5B5CEB), LightColors.primary)
        assertEquals(Color(0xFFF5F5FA), LightColors.background)
        assertEquals(Color(0xFF8E8FFF), DarkColors.primary)
        assertEquals(Color(0xFF0C0D18), DarkColors.background)
    }

    @Test
    @Config(qualifiers = "night")
    fun withoutChoiceThemeFollowsDevice() {
        composeRule.setContent {
            ClosetApp(FakeClothesRepository(), InMemoryThemeStore(), FakeAuthRepository(), FakeGoogleSignIn(), FakeShopsRepository())
        }

        composeRule.onNodeWithTag("tab_MY").performClick()

        composeRule.onNodeWithTag("theme_DARK").assertIsSelected()
    }

    @Test
    fun choosingThemeInMyTabIsSavedAndApplied() {
        val store = InMemoryThemeStore(AppTheme.LIGHT)
        composeRule.setContent {
            ClosetApp(
                FakeClothesRepository(),
                store,
                FakeAuthRepository(),
                FakeGoogleSignIn(),
                FakeShopsRepository(),
            )
        }
        composeRule.onNodeWithTag("tab_MY").performClick()

        composeRule.onNodeWithTag("theme_DARK").performClick()

        assertEquals(AppTheme.DARK, store.load())
        composeRule.onNodeWithTag("theme_DARK").assertIsSelected()
        composeRule.onNodeWithTag("theme_LIGHT").assertIsNotSelected()
    }
}
