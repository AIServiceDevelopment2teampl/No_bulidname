package com.team12.closet

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.team12.closet.data.ApiResult
import com.team12.closet.data.TranslateRepository
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.shop.MAX_TRANSLATE_REQUESTS_PER_PAGE
import com.team12.closet.ui.shop.PageBridge
import com.team12.closet.ui.theme.InMemoryThemeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

class FakeTranslateRepository(
    var fail: Boolean = false,
) : TranslateRepository {
    val requests = mutableListOf<List<String>>()

    override suspend fun translate(texts: List<String>): ApiResult<List<String>> {
        requests += texts
        return if (fail) ApiResult.Error("번역하지 못했어요") else ApiResult.Ok(texts.map { "번역:$it" })
    }
}

/** translation/page-translation.md: 앱이 translate.js와 서버 사이를 잇는 부분 (서버는 가짜) */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class TranslationInBrowserTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val translator = FakeTranslateRepository()

    private fun openZozo() {
        composeRule.setContent {
            ClosetApp(
                FakeClothesRepository(),
                InMemoryThemeStore(),
                FakeAuthRepository(),
                FakeGoogleSignIn(),
                FakeShopsRepository(),
                FakeProductsRepository(),
                translator,
            )
        }
        composeRule.onNodeWithTag("tab_SHOPPING").performClick()
        composeRule.onNodeWithTag("shop_https://zozo.jp/").performClick()
        composeRule.onNodeWithTag("browser").assertIsDisplayed()
    }

    private fun webView(view: View = composeRule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) webView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun bridge() = shadowOf(webView()!!).getJavascriptInterface("DupeCheckBridge") as PageBridge

    private fun settle() {
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()
    }

    private fun lastJs() = shadowOf(webView()!!).lastEvaluatedJavascript

    @Test
    fun pageRequestIsTranslatedByServerAndHandedBack() {
        openZozo()

        bridge().translate("7", """["手洗い可","Wool coat"]""")
        settle()

        assertEquals(listOf(listOf("手洗い可", "Wool coat")), translator.requests)
        val js = lastJs()
        assertTrue(js, js.contains("translate.apply(\"7\""))
        assertTrue(js, js.contains("번역:手洗い可") && js.contains("번역:Wool coat"))
    }

    @Test
    fun serverFailureTellsThePageToKeepTheOriginal() {
        translator.fail = true
        openZozo()

        bridge().translate("3", """["Wool coat"]""")
        settle()

        assertTrue(lastJs(), lastJs().contains("translate.fail(\"3\")"))
    }

    @Test
    fun barShowsLanguageWhenDoneAndSwitchShowsOriginal() {
        openZozo()

        bridge().onTranslateState("""{"status":"done","lang":"ja"}""")
        settle()

        composeRule.onNodeWithText("일본어 → 한국어로 바꿨어요").assertIsDisplayed()
        composeRule.onNodeWithTag("translation_switch").assertIsOn()

        composeRule.onNodeWithTag("translation_switch").performClick()

        assertTrue(lastJs(), lastJs().contains("translate.showOriginal()"))
        composeRule.onNodeWithTag("translation_switch").assertIsOff()
    }

    @Test
    fun showOriginalLinkAndTurningBackOn() {
        openZozo()
        bridge().onTranslateState("""{"status":"done","lang":"en"}""")
        settle()

        composeRule.onNodeWithTag("translation_show_original").performClick()
        assertTrue(lastJs().contains("translate.showOriginal()"))

        composeRule.onNodeWithTag("translation_switch").performClick()
        assertTrue(lastJs(), lastJs().contains("translate.showTranslation()"))
    }

    @Test
    fun koreanPageSaysSoWithoutSwitch() {
        openZozo()

        bridge().onTranslateState("""{"status":"korean","lang":"ko"}""")
        settle()

        composeRule.onNodeWithText("한국어 페이지예요").assertIsDisplayed()
        composeRule.onNodeWithTag("translation_switch").assertDoesNotExist()
    }

    @Test
    fun errorStateIsShown() {
        openZozo()

        bridge().onTranslateState("""{"status":"error","lang":"en"}""")
        settle()

        composeRule.onNodeWithText("번역하지 못했어요. 원문을 보여 드려요").assertIsDisplayed()
    }

    @Test
    fun newPageClearsTheBar() {
        openZozo()
        bridge().onTranslateState("""{"status":"done","lang":"ja"}""")
        settle()

        val view = webView()!!
        composeRule.runOnIdle { shadowOf(view).webViewClient.onPageStarted(view, "https://zozo.jp/category/", null) }

        composeRule.onNodeWithTag("translation_bar").assertDoesNotExist()
    }

    @Test
    fun brokenRequestFromPageFailsSafely() {
        openZozo()

        bridge().translate("9", "not json")
        settle()

        assertEquals(emptyList<List<String>>(), translator.requests)
        assertTrue(lastJs().contains("translate.fail(\"9\")"))
    }

    // 검수에서 찾은 문제: 악성 페이지가 번역 요청을 끝없이 보내 비용을 쓸 수 있었다
    @Test
    fun onePageCannotSendEndlessTranslateRequests() {
        openZozo()
        repeat(MAX_TRANSLATE_REQUESTS_PER_PAGE) { bridge().translate("$it", """["Item $it"]""") }
        settle()

        bridge().translate("over", """["One too many"]""")
        settle()

        assertEquals(MAX_TRANSLATE_REQUESTS_PER_PAGE, translator.requests.size)
        assertTrue(lastJs(), lastJs().contains("translate.fail(\"over\")"))
    }

    @Test
    fun limitResetsOnNextPage() {
        openZozo()
        repeat(MAX_TRANSLATE_REQUESTS_PER_PAGE) { bridge().translate("$it", """["Item $it"]""") }
        settle()

        val view = webView()!!
        composeRule.runOnIdle { shadowOf(view).webViewClient.onPageStarted(view, "https://zozo.jp/next/", null) }
        bridge().translate("next", """["Next page"]""")
        settle()

        assertEquals(listOf("Next page"), translator.requests.last())
    }
}
