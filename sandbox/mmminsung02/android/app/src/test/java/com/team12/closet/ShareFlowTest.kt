package com.team12.closet

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.team12.closet.data.ApiResult
import com.team12.closet.data.PhotoLoader
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.ProductField
import com.team12.closet.data.SharedItem
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.theme.InMemoryThemeStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** shopping/share-intake.md 완료 조건 [앱]: 공유로 받은 링크 → 인앱 브라우저, 이미지 → 분석 화면 (시안 19) */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class ShareFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val products = FakeProductsRepository()
    private val loaded = mutableListOf<String>()
    private var photo: PickedPhoto? = PickedPhoto("shot.png", "image/png", byteArrayOf(1, 2, 3))
    private val loader = PhotoLoader { uri -> photo.also { loaded += uri } }

    private fun open(
        shared: SharedItem,
        auth: FakeAuthRepository = FakeAuthRepository(),
    ) {
        composeRule.setContent {
            var item by remember { mutableStateOf<SharedItem?>(shared) }
            ClosetApp(
                FakeClothesRepository(),
                InMemoryThemeStore(),
                auth,
                FakeGoogleSignIn(),
                FakeShopsRepository(),
                products,
                shared = item,
                onSharedHandled = { item = null },
                photoLoader = loader,
            )
        }
        composeRule.waitForIdle()
    }

    private fun findWebView(view: View = composeRule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test
    fun sharedLinkOpensInAppBrowser() {
        open(SharedItem.Link("https://zozo.jp/shop/beams/goods/7781/"))

        composeRule.onNodeWithTag("browser").assertIsDisplayed()
        assertEquals("https://zozo.jp/shop/beams/goods/7781/", shadowOf(findWebView()!!).lastLoadedUrl)
    }

    @Test
    fun sharedTextWithoutLinkShowsMessage() {
        open(SharedItem.Problem("링크를 찾지 못했어요"))

        composeRule.onNodeWithText("링크를 찾지 못했어요").assertIsDisplayed()
    }

    @Test
    fun sharedImageGoesToResultScreen() {
        open(SharedItem.Screenshot("content://shots/1", extraCount = 0))

        composeRule.onNodeWithText("공유로 받은 상품").assertIsDisplayed()
        assertEquals(listOf("content://shots/1"), loaded)
        assertEquals(listOf("shot.png"), products.screenshots.map { it.name })
        composeRule.onNodeWithText("다른 쇼핑 앱에서 공유한 스크린샷 1장").assertIsDisplayed()
        composeRule.onNodeWithText("울 블렌드 크루넥 카디건").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("similar_7").performScrollTo().assertIsDisplayed()
        // 시안 19는 리포트를 펼친 채로 보여 준다
        composeRule.onNodeWithText("비슷한 점").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("구매는 직접 판단해 주세요. 비교는 사진과 적힌 정보로만 했어요.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unclearFieldShowsUncertainBadge() {
        val sample = FakeProductsRepository.SAMPLE_SCREENSHOT
        val fields = sample.product.fields + ("material" to ProductField("ウール50% アクリル50%", "울 50%, 아크릴 50%", uncertain = true))
        products.screenshotResult = ApiResult.Ok(sample.copy(product = sample.product.copy(fields = fields)))

        open(SharedItem.Screenshot("content://shots/1", extraCount = 0))

        composeRule.onNodeWithTag("uncertain_material").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("uncertain_name").assertDoesNotExist()
    }

    @Test
    fun severalImagesTellOnlyFirstWasRead() {
        open(SharedItem.Screenshot("content://shots/1", extraCount = 2))

        composeRule.onNodeWithText("사진 3장을 받았어요. 첫 장만 읽었어요").assertIsDisplayed()
        assertEquals(1, products.screenshots.size)
    }

    @Test
    fun serverMessageIsShownAndRetryWorks() {
        products.screenshotResult = ApiResult.Error("글자를 읽지 못했어요. 상품 설명이 보이게 다시 캡처해 주세요")
        open(SharedItem.Screenshot("content://shots/1", extraCount = 0))

        composeRule.onNodeWithText("글자를 읽지 못했어요. 상품 설명이 보이게 다시 캡처해 주세요").assertIsDisplayed()

        products.screenshotResult = ApiResult.Ok(FakeProductsRepository.SAMPLE_SCREENSHOT)
        composeRule.onNodeWithText("다시 시도").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("울 블렌드 크루넥 카디건").performScrollTo().assertIsDisplayed()
        assertEquals(2, products.screenshots.size)
    }

    @Test
    fun noClothingPhotoShowsCardAndNotice() {
        products.screenshotResult =
            ApiResult.Ok(
                FakeProductsRepository.SAMPLE_SCREENSHOT.copy(
                    similar = null,
                    report = null,
                    notice = "스크린샷에서 옷 사진을 찾지 못해 비슷한 옷은 찾지 않았어요",
                ),
            )

        open(SharedItem.Screenshot("content://shots/1", extraCount = 0))

        composeRule.onNodeWithText("스크린샷에서 옷 사진을 찾지 못해 비슷한 옷은 찾지 않았어요").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("울 블렌드 크루넥 카디건").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("similar_section").assertDoesNotExist()
    }

    @Test
    fun unreadableSharedFileShowsMessage() {
        photo = null

        open(SharedItem.Screenshot("content://shots/1", extraCount = 0))

        composeRule.onNodeWithText("사진을 열지 못했어요").assertIsDisplayed()
        assertEquals(0, products.screenshots.size)
    }

    @Test
    fun shareWaitsUntilLogin() {
        open(SharedItem.Screenshot("content://shots/1", extraCount = 0), auth = FakeAuthRepository(loggedIn = false))
        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
        assertEquals(0, products.screenshots.size)

        composeRule.onNodeWithTag("login_google").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("공유로 받은 상품").assertIsDisplayed()
        assertEquals(1, products.screenshots.size)
    }

    @Test
    fun backGoesHome() {
        open(SharedItem.Screenshot("content://shots/1", extraCount = 0))

        composeRule.onNodeWithTag("share_back").performClick()

        composeRule.onNodeWithTag("screen_HOME").assertIsDisplayed()
        composeRule.onNodeWithText("공유로 받은 상품").assertDoesNotExist()
    }
}
