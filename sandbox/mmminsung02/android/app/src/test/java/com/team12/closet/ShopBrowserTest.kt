package com.team12.closet

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.shop.BrowserScreen
import com.team12.closet.ui.shop.BrowserState
import com.team12.closet.ui.theme.ClosetTheme
import com.team12.closet.ui.theme.InMemoryThemeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** shopping/shop-browser.md 완료 조건 [앱] (서버·Room은 가짜) */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class ShopBrowserTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val shops = FakeShopsRepository()

    private fun openShoppingTab() {
        composeRule.setContent {
            ClosetApp(
                FakeClothesRepository(),
                InMemoryThemeStore(),
                FakeAuthRepository(),
                FakeGoogleSignIn(),
                shops,
                FakeProductsRepository(),
            )
        }
        composeRule.onNodeWithTag("tab_SHOPPING").performClick()
    }

    /** LazyColumn은 화면 밖 줄을 아직 만들지 않아서 목록을 그 줄까지 굴린다 */
    private fun scrollListTo(tag: String) {
        composeRule.onNodeWithTag("shop_list").performScrollToNode(hasTestTag(tag))
    }

    private fun findWebView(view: View = composeRule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test
    fun shopListShowsShippingTags() {
        openShoppingTab()

        // '국내'는 필터 칩에도 있어서 무신사 줄 안에서 찾는다
        composeRule
            .onNode(hasTestTag("shop_https://www.musinsa.com/") and hasAnyDescendant(hasText("국내")), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("배송대행 필요").assertIsDisplayed()
        composeRule.onNodeWithText("그 외").assertIsDisplayed()
        composeRule.onNodeWithTag("shop_https://www.asos.com/").assertIsDisplayed()
        composeRule.onNodeWithText("asos.com · 영어 → 번역").assertIsDisplayed()
        composeRule.onNodeWithText("ssense.com · 한국어 페이지 있음").assertIsDisplayed()
    }

    @Test
    fun filterChipsNarrowTheList() {
        openShoppingTab()

        composeRule.onNodeWithTag("shop_filter_TRANSLATE").performClick()

        composeRule.onNodeWithTag("shop_https://www.asos.com/").assertIsDisplayed()
        composeRule.onNodeWithTag("shop_https://www.musinsa.com/").assertDoesNotExist()
        composeRule.onNodeWithTag("shop_https://www.ssense.com/").assertDoesNotExist()

        composeRule.onNodeWithTag("shop_filter_DOMESTIC").performClick()
        composeRule.onNodeWithTag("shop_https://www.musinsa.com/").assertIsDisplayed()
        composeRule.onNodeWithTag("shop_https://zozo.jp/").assertDoesNotExist()
    }

    @Test
    fun tappingShopOpensBrowserWithItsUrl() {
        openShoppingTab()

        composeRule.onNodeWithTag("shop_https://zozo.jp/").performClick()

        composeRule.onNodeWithTag("browser").assertIsDisplayed()
        composeRule.onNodeWithTag("browser_url").assertIsDisplayed()
        val webView = findWebView()!!
        assertEquals("https://zozo.jp/", shadowOf(webView).lastLoadedUrl)
        assertTrue(webView.settings.javaScriptEnabled)
    }

    // 검수에서 찾은 문제: ☆로 추가 화면에 다녀오면 쇼핑몰 첫 화면부터 다시 열렸다
    @Test
    fun addingFromBrowserReturnsToTheSamePage() {
        openShoppingTab()
        composeRule.onNodeWithTag("shop_https://zozo.jp/").performClick()
        composeRule.onNodeWithTag("browser").assertIsDisplayed()
        // 사용자가 상품 페이지로 들어간 것처럼 페이지 시작을 알린다
        val webView = findWebView()!!
        composeRule.runOnIdle {
            shadowOf(webView).webViewClient.onPageStarted(webView, "https://zozo.jp/shop/beams/goods/7781/", null)
        }

        composeRule.onNodeWithTag("browser_add_shop").performClick()
        composeRule.onNodeWithTag("add_shop_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("add_shop_cancel").performClick()

        composeRule.onNodeWithTag("browser").assertIsDisplayed()
        assertEquals("https://zozo.jp/shop/beams/goods/7781/", shadowOf(findWebView()!!).lastLoadedUrl)
    }

    @Test
    fun closingBrowserReturnsToList() {
        openShoppingTab()
        composeRule.onNodeWithTag("shop_https://zozo.jp/").performClick()

        composeRule.onNodeWithTag("browser_close").performClick()

        composeRule.onNodeWithTag("shop_list").assertIsDisplayed()
    }

    @Test
    fun typedAddressWithoutSchemeOpensAsHttps() {
        openShoppingTab()

        composeRule.onNodeWithTag("address_field").performTextInput("cos.com")
        composeRule.onNodeWithTag("address_go").performClick()

        composeRule.onNodeWithTag("browser").assertIsDisplayed()
        assertEquals("https://cos.com", shadowOf(findWebView()!!).lastLoadedUrl)
    }

    @Test
    fun httpAddressIsNotOpened() {
        openShoppingTab()

        composeRule.onNodeWithTag("address_field").performTextInput("http://example.com")
        composeRule.onNodeWithTag("address_go").performClick()

        composeRule.onNodeWithText("https:// 로 시작하는 주소만 열 수 있어요").assertIsDisplayed()
        composeRule.onNodeWithTag("browser").assertDoesNotExist()
    }

    @Test
    fun loadFailureShowsErrorScreenAndRetryHidesIt() {
        val state = BrowserState("https://zozo.jp/")
        composeRule.setContent { ClosetTheme(darkTheme = false) { BrowserScreen(state, {}, { _, _ -> }, { "" }) } }
        composeRule.onNodeWithTag("browser_error").assertDoesNotExist()

        composeRule.runOnIdle { state.onMainFrameError() }

        composeRule.onNodeWithText("페이지를 열지 못했어요").assertIsDisplayed()
        composeRule.onNodeWithText("외부 브라우저로 열기").assertIsDisplayed()
        composeRule.onNodeWithTag("browser_retry").performClick()
        composeRule.onNodeWithTag("browser_error").assertDoesNotExist()
    }

    @Test
    fun insecureLinkAsksBeforeOpening() {
        val state = BrowserState("https://zozo.jp/")
        composeRule.setContent { ClosetTheme(darkTheme = false) { BrowserScreen(state, {}, { _, _ -> }, { "" }) } }

        composeRule.runOnIdle { state.askToOpen = "http://insecure.example/" }

        composeRule.onNodeWithText("보안 연결이 아닌 주소예요").assertIsDisplayed()
    }

    @Test
    fun addedShopAppearsAndCanBeRemoved() {
        openShoppingTab()

        scrollListTo("add_shop")
        composeRule.onNodeWithTag("add_shop").performClick()
        composeRule.onNodeWithTag("add_shop_name").performTextReplacement("COS")
        composeRule.onNodeWithTag("add_shop_url").performTextReplacement("https://www.cos.com/")
        composeRule.onNodeWithTag("add_shop_confirm").performClick()

        scrollListTo("shop_https://www.cos.com/")
        composeRule.onNodeWithTag("shop_https://www.cos.com/").assertIsDisplayed()
        composeRule.onNodeWithText("cos.com · 내가 추가").assertIsDisplayed()

        composeRule.onNodeWithTag("remove_https://www.cos.com/").performClick()
        composeRule.onNodeWithTag("shop_https://www.cos.com/").assertDoesNotExist()
    }

    @Test
    fun nonHttpsAndExistingShopsAreNotAdded() {
        openShoppingTab()
        scrollListTo("add_shop")
        composeRule.onNodeWithTag("add_shop").performClick()
        composeRule.onNodeWithTag("add_shop_name").performTextReplacement("X")

        composeRule.onNodeWithTag("add_shop_url").performTextReplacement("http://x.example/")
        composeRule.onNodeWithTag("add_shop_confirm").performClick()
        composeRule.onNodeWithText("https:// 로 시작하는 주소만 추가할 수 있어요").assertIsDisplayed()

        composeRule.onNodeWithTag("add_shop_url").performTextReplacement("https://asos.com")
        composeRule.onNodeWithTag("add_shop_confirm").performClick()
        composeRule.onNodeWithText("이미 목록에 있는 쇼핑몰이에요").assertIsDisplayed()

        assertEquals(emptyList<Any>(), shops.myShops.value)
    }

    @Test
    fun homeCardsGoToShoppingAndCloset() {
        composeRule.setContent {
            ClosetApp(
                FakeClothesRepository(),
                InMemoryThemeStore(),
                FakeAuthRepository(),
                FakeGoogleSignIn(),
                shops,
                FakeProductsRepository(),
            )
        }

        composeRule.onNodeWithTag("home_shop").performClick()
        composeRule.onNodeWithTag("shop_list").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_HOME").performClick()
        composeRule.onNodeWithTag("home_closet").performClick()
        composeRule.onNodeWithTag("screen_CLOSET").assertIsDisplayed()
    }
}
