package com.team12.closet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ConfirmResult
import com.team12.closet.data.ImportBatch
import com.team12.closet.data.ImportCandidate
import com.team12.closet.data.ImportChoice
import com.team12.closet.data.ImportKind
import com.team12.closet.data.ImportsRepository
import com.team12.closet.data.PickedPhoto
import com.team12.closet.ui.register.ImportPicks
import com.team12.closet.ui.register.ImportScreen
import com.team12.closet.ui.register.ImportViewModel
import com.team12.closet.ui.shop.OrderCard
import com.team12.closet.ui.shop.PageInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeImportsRepository(
    var batch: ImportBatch = sampleBatch(),
) : ImportsRepository {
    val sentPhotos = mutableListOf<List<PickedPhoto>>()
    val confirmed = mutableListOf<List<ImportChoice>>()
    val discarded = mutableListOf<Long>()
    var readError: String? = null

    override suspend fun fromPhotos(
        kind: ImportKind,
        photos: List<PickedPhoto>,
    ): ApiResult<ImportBatch> {
        sentPhotos += photos
        return readError?.let { ApiResult.Error(it) } ?: ApiResult.Ok(batch)
    }

    override suspend fun fromOrder(orderJson: String) = ApiResult.Ok(batch)

    override suspend fun confirm(
        id: Long,
        choices: List<ImportChoice>,
    ): ApiResult<ConfirmResult> {
        confirmed += choices
        return ApiResult.Ok(ConfirmResult(saved = emptyList()))
    }

    override suspend fun discard(id: Long): ApiResult<Unit> {
        discarded += id
        return ApiResult.Ok(Unit)
    }

    override fun imageUrl(path: String?) = null

    companion object {
        fun sampleBatch(type: String = "order_capture") =
            ImportBatch(
                id = 7,
                type = type,
                status = "pending",
                candidates =
                    listOf(
                        ImportCandidate(0, "오버핏 코튼 셔츠", color = "white", size = "L", category = "top", hasImage = true),
                        ImportCandidate(1, "울 니트 베스트", size = "M", category = "top", hasImage = false),
                        ImportCandidate(2, "핸드크림 50ml", isClothing = false, selected = false),
                        ImportCandidate(3, "카멜 코트", category = "outer", duplicate = true, selected = false, hasImage = true),
                    ),
            )
    }
}

private fun photo(name: String) = PickedPhoto(name, "image/png", byteArrayOf(1))

/** 자동 등록(기능 13~15) 앱 쪽: 후보 확인 화면, 결제 완료 카드, 주문 완료 페이지 주입 */
@RunWith(RobolectricTestRunner::class)
class AutoRegisterTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeImportsRepository()

    @Test
    fun defaultPicksFollowServerAndColorlessPhotolessNeedsAColor() {
        val picks = ImportPicks(FakeImportsRepository.sampleBatch())
        // 서버 기본 체크: 의류 아님·이미 등록된 옷은 해제. 1번은 사진도 색도 없어 저장 전에 색을 골라야 한다
        assertEquals(2, picks.selectedCount)
        assertFalse(picks.canSave)
        picks.update(1, picks.edit(1).copy(color = "gray"))
        assertTrue(picks.canSave)
        assertEquals(
            listOf(ImportChoice(0, "top", "white", null), ImportChoice(1, "top", "gray", null)),
            picks.choices(),
        )
    }

    @Test
    fun orderCaptureReviewSavesOnlyChosenCandidates() {
        val viewModel = ImportViewModel(repository, ImportKind.ORDER_CAPTURE)
        composeRule.setContent {
            ImportScreen(viewModel, imageUrl = { null }, onPick = { viewModel.read(listOf(photo("a.png"), photo("b.png"))) }, onDone = {})
        }
        composeRule.onNodeWithTag("import_pick").performClick()

        composeRule.onNodeWithText("주문내역 캡처 2장에서 상품 4개를 찾았어요. 틀린 건 눌러서 고쳐 주세요.").assertIsDisplayed()
        composeRule.onNodeWithText("이미 등록된 옷이에요").assertExists()
        composeRule.onNodeWithText("의류 아님").assertExists()
        composeRule.onNodeWithText("주문자 이름·주소·연락처는 읽지도 저장하지도 않아요.").assertExists()
        // 사진 없는 베스트는 색을 골라야 저장할 수 있다 → 체크를 풀면 저장된다
        composeRule.onNodeWithTag("import_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("candidate_check_1").performClick()
        composeRule.onNodeWithTag("import_save").assertIsEnabled().performClick()

        composeRule.onNodeWithTag("import_saved").assertIsDisplayed()
        assertEquals(listOf(listOf(ImportChoice(0, "top", "white", null))), repository.confirmed)
    }

    @Test
    fun leavingReviewDiscardsCandidates() {
        val viewModel = ImportViewModel(repository, ImportKind.RECEIPT)
        composeRule.setContent {
            ImportScreen(
                viewModel,
                imageUrl = { null },
                onPick = { viewModel.read(listOf(photo("r.png"))) },
                onDone = {},
            )
        }
        composeRule.onNodeWithTag("import_pick").performClick()
        composeRule.onNodeWithTag("import_back").performClick()

        composeRule.waitForIdle()
        assertEquals(listOf(7L), repository.discarded)
        assertTrue(repository.confirmed.isEmpty())
    }

    @Test
    fun unreadablePhotoShowsServerMessage() {
        repository.readError = "주문내역이 잘 보이게 다시 캡처해 주세요"
        val viewModel = ImportViewModel(repository, ImportKind.ORDER_CAPTURE)
        composeRule.setContent {
            ImportScreen(
                viewModel,
                imageUrl = { null },
                onPick = { viewModel.read(listOf(photo("x.png"))) },
                onDone = {},
            )
        }
        composeRule.onNodeWithTag("import_pick").performClick()

        composeRule.onNodeWithText("주문내역이 잘 보이게 다시 캡처해 주세요").assertIsDisplayed()
    }

    @Test
    fun orderCardClosedSavesNothing() {
        var closed = false
        composeRule.setContent {
            OrderCard(picks = ImportPicks(FakeImportsRepository.sampleBatch("in_app")), imports = repository, onClosed = { closed = true })
        }
        composeRule.onNodeWithText("옷장에 추가할까요?").assertIsDisplayed()
        composeRule.onNodeWithText("이름·주소·연락처·결제 정보는 읽지 않아요.").assertExists()
        composeRule.onNodeWithTag("order_close").performClick()

        composeRule.waitForIdle()
        assertTrue(closed)
        assertEquals(listOf(7L), repository.discarded)
        assertTrue(repository.confirmed.isEmpty())
    }

    @Test
    fun orderCardAddsOnlyCheckedItems() {
        composeRule.setContent {
            OrderCard(picks = ImportPicks(FakeImportsRepository.sampleBatch("in_app")), imports = repository, onClosed = {})
        }
        composeRule.onNodeWithTag("candidate_check_1").performClick()
        composeRule.onNodeWithText("옷장에 추가 (1)").performClick()

        composeRule.onNodeWithTag("order_saved").assertIsDisplayed()
        assertEquals(listOf(listOf(ImportChoice(0, "top", "white", null))), repository.confirmed)
        assertTrue(repository.discarded.isEmpty())
    }

    @Test
    fun orderCompletePageGetsOnlyTheOrderScript() {
        val injected = mutableListOf<String>()
        val injector = PageInjector({ "ALL" }, { "ORDER" }) { injected += it }
        listOf(
            "https://zozo.jp/_cart/order/complete.html",
            "https://shop.example.com/checkout/complete?id=1",
            "https://www.asos.com/checkout/order-confirmation/123",
            "https://www.asos.com/identity/login?returnUrl=/checkout/complete",
            "https://shop.example.com/payment/complete",
            "https://shop.example.com/checkout/",
        ).forEach { url ->
            injector.onPageStarted()
            injector.onPageFinished(url)
        }
        // 완료 페이지 셋만 order.js. 로그인·결제·결제 전 화면에는 아무것도 넣지 않는다 (zozo 완료 주소는 민감 주소가 아니라 전체)
        assertEquals(listOf("ALL", "ORDER", "ORDER"), injected)
    }
}
