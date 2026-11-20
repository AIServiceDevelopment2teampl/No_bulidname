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
import com.team12.closet.data.PhotoLoader
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.SharedItem
import com.team12.closet.ui.ClosetApp
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

/**
 * 게스트로 둘러보기 (2026-11-20 사용자 결정): 게스트는 서버 기능을 부르지 않고,
 * 그 자리에 'Google로 로그인하면 쓸 수 있어요' 안내와 로그인 버튼을 보여 준다.
 * 쇼핑몰 목록·인앱 브라우저 구경은 그대로 된다
 */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class GuestModeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val auth = FakeAuthRepository().apply { loginAsGuest() }
    private val clothes = FakeClothesRepository()
    private val products = FakeProductsRepository()
    private val translator = FakeTranslateRepository()
    private val clerk = FakeClerkRepository()
    private val loadedPhotos = mutableListOf<String>()
    private val loader = PhotoLoader { uri -> PickedPhoto("shot.png", "image/png", byteArrayOf(1, 2, 3)).also { loadedPhotos += uri } }

    private fun start(shared: SharedItem? = null) {
        composeRule.setContent {
            ClosetApp(
                clothes,
                InMemoryThemeStore(),
                auth,
                FakeGoogleSignIn(),
                FakeShopsRepository(),
                products,
                translator,
                shared = shared,
                photoLoader = loader,
                clerkRepository = clerk,
            )
        }
    }

    private fun openZozo() {
        start()
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

    @Test
    fun closetTabShowsLoginGuideWithoutAskingServer() {
        start()

        composeRule.onNodeWithTag("tab_CLOSET").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("login_required").assertIsDisplayed()
        composeRule.onNodeWithText("Google로 로그인하면 내 옷장을 쓸 수 있어요").assertIsDisplayed()
        assertEquals(emptyList<Any>(), clothes.listRequests)
    }

    @Test
    fun loginButtonGoesBackToLoginScreen() {
        start()
        composeRule.onNodeWithTag("tab_CLOSET").performClick()

        composeRule.onNodeWithTag("login_required_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, auth.logoutCalls)
        composeRule.onNodeWithTag("login_guest").assertIsDisplayed()
    }

    @Test
    fun productPageShowsLoginGuideInsteadOfAnalysis() {
        openZozo()

        bridge().onProduct("""{"url":"https://zozo.jp/shop/beams/goods/7781/","json_ld":[],"og":{},"text":"..."}""")
        settle()

        assertEquals(emptyList<String>(), products.extracted)
        composeRule.onNodeWithTag("product_bubble").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_required").assertIsDisplayed()
        composeRule.onNodeWithText("Google로 로그인하면 이 상품을 내 옷장과 비교해 드려요").assertIsDisplayed()
        // AI 점원도 서버 기능이라 부르지 않는다
        assertEquals(emptyList<Long?>(), clerk.historyFor)
        assertEquals(emptyList<Long>(), clerk.matchingFor)
    }

    @Test
    fun translationIsNotSentAndBarExplainsLogin() {
        openZozo()

        bridge().translate("7", """["手洗い可"]""")
        settle()

        assertEquals(emptyList<List<String>>(), translator.requests)
        val js = shadowOf(webView()!!).lastEvaluatedJavascript
        assertTrue(js, js.contains("translate.fail(\"7\")"))
        bridge().onTranslateState("""{"status":"error","lang":"ja"}""")
        settle()
        composeRule.onNodeWithText("Google로 로그인하면 한국어로 바꿔 드려요").assertIsDisplayed()
    }

    @Test
    fun sharedScreenshotShowsLoginGuideWithoutUploading() {
        start(SharedItem.Screenshot("content://shots/1", extraCount = 0))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("login_required").assertIsDisplayed()
        composeRule.onNodeWithText("Google로 로그인하면 캡처한 상품을 내 옷장과 비교해 드려요").assertIsDisplayed()
        assertEquals(emptyList<String>(), loadedPhotos)
        assertEquals(0, products.screenshots.size)
    }

    @Test
    fun sharedLinkStillOpensInBrowser() {
        start(SharedItem.Link("https://zozo.jp/shop/beams/goods/7781/"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("browser").assertIsDisplayed()
    }
}
