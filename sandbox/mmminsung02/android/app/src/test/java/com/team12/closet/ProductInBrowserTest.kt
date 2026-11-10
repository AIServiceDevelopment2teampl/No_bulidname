package com.team12.closet

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ReportItem
import com.team12.closet.data.SimilarResult
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.shop.PageBridge
import com.team12.closet.ui.shop.ProductCard
import com.team12.closet.ui.theme.ClosetTheme
import com.team12.closet.ui.theme.InMemoryThemeStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** 인앱 브라우저에서 상품 페이지를 만나면: 서버 정리 → 비슷한 옷 → 말풍선 → 패널 (서버는 가짜) */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class ProductInBrowserTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val products = FakeProductsRepository()
    private val pageJson = """{"url":"https://zozo.jp/shop/beams/goods/7781/","json_ld":[],"og":{},"text":"..."}"""

    private fun openZozo() {
        composeRule.setContent {
            ClosetApp(
                FakeClothesRepository(),
                InMemoryThemeStore(),
                FakeAuthRepository(),
                FakeGoogleSignIn(),
                FakeShopsRepository(),
                products,
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

    /** 페이지의 product.js가 window.DupeCheckBridge.onProduct(...)를 부른 것처럼 한다 */
    private fun pageReportsProduct(json: String = pageJson) {
        val bridge = shadowOf(findWebView()!!).getJavascriptInterface("DupeCheckBridge") as PageBridge
        bridge.onProduct(json)
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()
    }

    @Test
    fun bridgeIsRegisteredForTheProductScript() {
        openZozo()

        assertEquals(true, shadowOf(findWebView()!!).getJavascriptInterface("DupeCheckBridge") is PageBridge)
    }

    @Test
    fun productPageShowsBubbleWithSimilarCount() {
        openZozo()

        pageReportsProduct()

        assertEquals(listOf(pageJson), products.extracted)
        assertEquals(listOf(41L), products.similarFor)
        composeRule.onNodeWithText("비슷한 옷 1벌 있어요").assertIsDisplayed()
    }

    @Test
    fun tappingBubbleOpensPanelWithKoreanCardAndSimilarClothes() {
        openZozo()
        pageReportsProduct()

        composeRule.onNodeWithTag("product_bubble").performClick()

        composeRule.onNodeWithTag("product_panel").assertIsDisplayed()
        // 상품 카드는 TOP 3 아래에 있어 패널 안에서 굴려야 보인다
        composeRule.onNodeWithText("울 블렌드 크루넥 카디건").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("similar_7").assertIsDisplayed()
        composeRule.onNodeWithText("매우 비슷").assertIsDisplayed()
    }

    @Test
    fun samePageReportedTwiceIsSentOnce() {
        openZozo()

        pageReportsProduct()
        pageReportsProduct()

        assertEquals(1, products.extracted.size)
    }

    @Test
    fun navigatingToAnotherPageClearsTheProduct() {
        openZozo()
        pageReportsProduct()
        composeRule.onNodeWithTag("product_bubble").assertIsDisplayed()

        val webView = findWebView()!!
        composeRule.runOnIdle { shadowOf(webView).webViewClient.onPageStarted(webView, "https://zozo.jp/category/", null) }

        composeRule.onNodeWithTag("product_bubble").assertDoesNotExist()
    }

    @Test
    fun productWithoutPhotoStillShowsInfo() {
        products.extractResult = ApiResult.Ok(FakeProductsRepository.SAMPLE_PRODUCT.copy(hasImage = false))
        openZozo()
        pageReportsProduct()

        composeRule.onNodeWithTag("product_bubble").performClick()

        assertEquals(emptyList<Long>(), products.similarFor)
        composeRule.onNodeWithText("상품 사진을 찾지 못했어요").assertIsDisplayed()
        composeRule.onNodeWithText("울 블렌드 크루넥 카디건").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun serverFailureShowsMessageInBubble() {
        products.extractResult = ApiResult.Error("서버에 연결하지 못했어요")
        openZozo()

        pageReportsProduct()

        composeRule.onNodeWithText("상품 정보를 읽지 못했어요").assertIsDisplayed()
    }

    // 구매 전 비교 리포트 (compare/purchase-report.md, 시안 '7. AI 점원 패널'·'공유로 받은 상품')

    private fun openPanel() {
        openZozo()
        pageReportsProduct()
        composeRule.onNodeWithTag("product_bubble").performClick()
    }

    @Test
    fun reportIsRequestedAfterSimilarAndSummaryIsShown() {
        openPanel()

        assertEquals(listOf(41L), products.reportFor)
        composeRule.onNodeWithText("회색 카디건과 종류·색이 비슷하고, 소재는 내 옷 정보가 없어요.").performScrollTo().assertIsDisplayed()
        // 전체는 접혀 있다
        composeRule.onNodeWithText("비슷한 점").assertDoesNotExist()
    }

    @Test
    fun seeFullReportShowsThreePartsAndDisclaimer() {
        openPanel()

        composeRule.onNodeWithTag("report_toggle").performScrollTo().performClick()

        composeRule.onNodeWithText("비슷한 점").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("둘 다 그레이 계열이에요.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("확인 안 된 점").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("내 옷의 소재 정보가 없어요.").performScrollTo().assertIsDisplayed()
        // 비어 있는 부분은 제목도 보이지 않는다
        composeRule.onNodeWithText("다른 점").assertDoesNotExist()
        composeRule.onNodeWithText("구매는 직접 판단해 주세요. 비교는 사진과 적힌 정보로만 했어요.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reportWithoutSentencesShowsComparisonTable() {
        // LLM이 실패하면 서버는 문장 없이 비교표만 준다
        val table =
            FakeProductsRepository.SAMPLE_REPORT.copy(
                summary = null,
                similar = listOf(ReportItem("color", "색", "블랙", "블랙", "photo")),
                different = listOf(ReportItem("material", "소재", "울 100%", "면 100%")),
                unknown = listOf(ReportItem("fit", "핏", "레귤러 핏", null)),
            )
        products.reportResult = ApiResult.Ok(table)

        openPanel()

        composeRule.onNodeWithText("색: 새 상품 블랙 (사진 기준) · 내 옷 블랙").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("소재: 새 상품 울 100% · 내 옷 면 100%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("핏: 새 상품 레귤러 핏 · 내 옷 정보 없음").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noSimilarReportListsOnlyProductInfo() {
        products.similarResult = ApiResult.Ok(SimilarResult("no_similar", emptyList()))
        products.reportResult =
            ApiResult.Ok(
                FakeProductsRepository.SAMPLE_REPORT.copy(
                    status = "no_similar",
                    headline = "옷장에 비슷한 옷이 없어요",
                    cloth = null,
                    level = null,
                    summary = null,
                    similar = emptyList(),
                    unknown = listOf(ReportItem("material", "소재", "울 50%, 아크릴 50%", null)),
                ),
            )

        openPanel()

        composeRule.onNodeWithText("옷장에 비슷한 옷이 없어요").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("소재: 울 50%, 아크릴 50%").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reportFailureKeepsSimilarClothes() {
        products.reportResult = ApiResult.Error("비교 리포트를 만들지 못했어요")

        openPanel()

        composeRule.onNodeWithTag("similar_7").assertIsDisplayed()
        composeRule.onNodeWithText("비교 리포트를 만들지 못했어요").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyClosetDoesNotAskForReport() {
        products.similarResult = ApiResult.Ok(SimilarResult("empty_closet", emptyList()))

        openPanel()

        assertEquals(emptyList<Long>(), products.reportFor)
    }

    // 상품 카드 (translation/product-extraction.md 출력: 상품명/색상/소재/핏/사이즈/세탁, 원문 보기, 정보 없음)

    private fun showCard() {
        composeRule.setContent { ClosetTheme(darkTheme = false) { ProductCard(FakeProductsRepository.SAMPLE_PRODUCT) } }
    }

    @Test
    fun cardShowsKoreanAndUnknownFieldsAsNoInfo() {
        showCard()

        composeRule.onNodeWithText("울 50%, 아크릴 50%").assertIsDisplayed()
        composeRule.onNodeWithText("손세탁 가능").assertIsDisplayed()
        // 핏은 원문에 없어서 null → 정보 없음
        composeRule.onAllNodesWithText("정보 없음").assertCountEquals(1)
    }

    @Test
    fun sizeShowsConvertedUnits() {
        showCard()

        composeRule.onNodeWithText("총장: 63.5 cm (25 in)").assertIsDisplayed()
    }

    @Test
    fun originalIsHiddenUntilAsked() {
        showCard()
        composeRule.onAllNodes(hasText("ウール50% アクリル50%")).assertCountEquals(0)

        composeRule.onNodeWithTag("original_material").performClick()

        composeRule.onNodeWithText("ウール50% アクリル50%").assertIsDisplayed()
        composeRule.onNodeWithText("원문 숨기기").assertIsDisplayed()
    }
}
