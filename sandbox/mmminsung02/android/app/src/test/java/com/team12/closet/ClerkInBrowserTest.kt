package com.team12.closet

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ClerkMessage
import com.team12.closet.data.MatchingResult
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.shop.PageBridge
import com.team12.closet.ui.theme.InMemoryThemeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** compare/ai-clerk.md: 인앱 브라우저의 AI 점원 패널 (시안 7, 서버는 가짜) */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class ClerkInBrowserTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val products = FakeProductsRepository()
    private val clerk = FakeClerkRepository()

    private fun openZozo() {
        composeRule.setContent {
            ClosetApp(
                FakeClothesRepository(),
                InMemoryThemeStore(),
                FakeAuthRepository(),
                FakeGoogleSignIn(),
                FakeShopsRepository(),
                products,
                clerkRepository = clerk,
            )
        }
        composeRule.onNodeWithTag("tab_SHOPPING").performClick()
        composeRule.onNodeWithTag("shop_https://zozo.jp/").performClick()
        composeRule.onNodeWithTag("browser").assertIsDisplayed()
    }

    private fun findWebView(view: View = composeRule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun pageReportsProduct() {
        val bridge = shadowOf(findWebView()!!).getJavascriptInterface("DupeCheckBridge") as PageBridge
        bridge.onProduct("""{"url":"https://zozo.jp/shop/beams/goods/7781/","json_ld":[],"og":{},"text":"..."}""")
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()
    }

    private fun openPanel() {
        openZozo()
        pageReportsProduct()
        composeRule.onNodeWithTag("product_bubble").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun openingAndClosingPanelKeepsPageScrollPosition() {
        openZozo()
        pageReportsProduct()
        val webView = findWebView()!!
        composeRule.runOnIdle { webView.scrollTo(0, 700) }

        composeRule.onNodeWithTag("product_bubble").performClick()
        composeRule.onNodeWithTag("product_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("product_panel_close").performClick()
        composeRule.waitForIdle()

        // 같은 웹뷰가 그대로 남아 있고 스크롤도 그대로다 (패널은 페이지 위에 겹쳐 그린다)
        assertSame(webView, findWebView())
        assertEquals(700, webView.scrollY)
    }

    @Test
    fun panelShowsMatchingClothesFromCloset() {
        openPanel()

        assertEquals(listOf(41L), clerk.matchingFor)
        composeRule.onNodeWithText("이 상품과 어울리는 내 옷").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("matching_3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("어두운 톤끼리 잘 어울려요").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reopeningPanelDoesNotAskAgain() {
        openPanel()
        composeRule.onNodeWithTag("product_panel_close").performClick()
        composeRule.onNodeWithTag("product_bubble").performClick()
        composeRule.waitForIdle()

        assertEquals(1, clerk.matchingFor.size)
        assertEquals(1, clerk.historyFor.size)
    }

    @Test
    fun emptyClosetShowsRegisterGuideInsteadOfPicks() {
        clerk.matchingResult =
            ApiResult.Ok(MatchingResult("empty_closet", emptyList(), "옷장에 옷을 등록하면 어울리는 옷을 골라 드려요"))

        openPanel()

        composeRule.onNodeWithText("옷장에 옷을 등록하면 어울리는 옷을 골라 드려요").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun previousConversationIsShownAndNewQuestionIsAnswered() {
        clerk.historyResult =
            ApiResult.Ok(listOf(ClerkMessage(1, "user", "이거 두꺼워?"), ClerkMessage(2, "assistant", "페이지에 그 정보가 없어요")))
        clerk.askResult = ApiResult.Ok(ClerkMessage(4, "assistant", "손세탁할 수 있어요."))
        openPanel()

        assertEquals(listOf<Long?>(41L), clerk.historyFor)
        composeRule.onNodeWithText("페이지에 그 정보가 없어요").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("clerk_input").performTextInput("세탁기 돌려도 돼?")
        composeRule.onNodeWithTag("clerk_send").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<Pair<Long?, String>>(41L to "세탁기 돌려도 돼?"), clerk.asked)
        composeRule.onNodeWithText("세탁기 돌려도 돼?").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("손세탁할 수 있어요.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun failedQuestionShowsMessage() {
        clerk.askResult = ApiResult.Error("답하지 못했어요. 잠시 후 다시 시도해 주세요")
        openPanel()

        composeRule.onNodeWithTag("clerk_input").performTextInput("세탁은?")
        composeRule.onNodeWithTag("clerk_send").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("답하지 못했어요. 잠시 후 다시 시도해 주세요").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun offProductPageClerkTakesGeneralQuestions() {
        openZozo()

        composeRule.onNodeWithTag("clerk_bubble").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("상품 페이지에서 누르면 내 옷장과 비교해 드려요").assertIsDisplayed()
        assertEquals(listOf<Long?>(null), clerk.historyFor)
        composeRule.onNodeWithTag("clerk_input").performTextInput("해외 사이즈는 어떻게 골라?")
        composeRule.onNodeWithTag("clerk_send").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<Pair<Long?, String>>(null to "해외 사이즈는 어떻게 골라?"), clerk.asked)
        assertEquals(emptyList<Long>(), clerk.matchingFor)
    }
}
