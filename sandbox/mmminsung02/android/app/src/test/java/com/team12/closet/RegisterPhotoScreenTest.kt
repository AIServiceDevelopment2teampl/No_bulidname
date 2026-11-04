package com.team12.closet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.team12.closet.FakeClothesRepository.Companion.photo
import com.team12.closet.FakeClothesRepository.Companion.registered
import com.team12.closet.data.UploadOutcome
import com.team12.closet.ui.register.RegisterPhotoScreen
import com.team12.closet.ui.register.RegisterPhotoViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** wardrobe/photo-register.md 완료 조건 [앱]: 사진 선택 → 업로드 → 분류 결과 확인 화면 (서버는 가짜) */
@RunWith(RobolectricTestRunner::class)
class RegisterPhotoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeClothesRepository()
    private val viewModel = RegisterPhotoViewModel(repository)

    private fun showScreen(vararg picked: String) {
        composeRule.setContent {
            RegisterPhotoScreen(
                viewModel = viewModel,
                // 시스템 사진 선택기 대신 고른 사진을 바로 넘긴다
                onPickPhotos = { viewModel.upload(picked.map { photo(it) }) },
                onDone = {},
            )
        }
    }

    @Test
    fun pickPhotoUploadsAndShowsClassificationResult() {
        repository.willReturn("shirt.jpg", registered(category = "top", color = "black"))
        showScreen("shirt.jpg")

        composeRule.onNodeWithTag("pick_photos").performClick()

        composeRule.onNodeWithTag("result_shirt.jpg").assertIsDisplayed()
        composeRule.onNodeWithText("상의 · 블랙").assertIsDisplayed()
        composeRule.onNodeWithText("미확인").assertIsDisplayed()
        assertEquals(listOf("shirt.jpg"), repository.uploaded.map { it.name })
    }

    @Test
    fun severalPhotosAreUploadedOneByOne() {
        repository.willReturn("a.jpg", registered(category = "top", color = "white"))
        repository.willReturn("b.jpg", registered(category = "bottom", color = "navy"))
        showScreen("a.jpg", "b.jpg")

        composeRule.onNodeWithTag("pick_photos").performClick()

        composeRule.onNodeWithText("상의 · 화이트").assertIsDisplayed()
        composeRule.onNodeWithText("하의 · 네이비").assertIsDisplayed()
        assertEquals(listOf("a.jpg", "b.jpg"), repository.uploaded.map { it.name })
    }

    @Test
    fun lowConfidenceAsksUserToChooseCategory() {
        repository.willReturn("x.jpg", registered(category = "other", color = "gray", needsCategory = true))
        showScreen("x.jpg")

        composeRule.onNodeWithTag("pick_photos").performClick()

        composeRule.onNodeWithText("종류를 골라 주세요").assertIsDisplayed()
    }

    @Test
    fun duplicatePhotoShowsAlreadyRegistered() {
        repository.willReturn("dup.jpg", registered(category = "top", color = "black", duplicate = true))
        showScreen("dup.jpg")

        composeRule.onNodeWithTag("pick_photos").performClick()

        composeRule.onNodeWithText("이미 등록된 사진이에요").assertIsDisplayed()
    }

    @Test
    fun rejectedPhotoShowsServerMessage() {
        repository.willReturn("note.jpg", UploadOutcome.Rejected("이미지를 읽을 수 없어요"))
        showScreen("note.jpg")

        composeRule.onNodeWithTag("pick_photos").performClick()

        composeRule.onNodeWithText("이미지를 읽을 수 없어요").assertIsDisplayed()
    }

    @Test
    fun serverFailureShowsRetryAndRetrySucceeds() {
        repository.willReturn(
            "coat.jpg",
            UploadOutcome.Failed("서버에 연결하지 못했어요"),
            registered(category = "outer", color = "beige"),
        )
        showScreen("coat.jpg")
        composeRule.onNodeWithTag("pick_photos").performClick()
        composeRule.onNodeWithText("서버에 연결하지 못했어요").assertIsDisplayed()

        composeRule.onNodeWithTag("retry_coat.jpg").performClick()

        composeRule.onNodeWithText("아우터 · 베이지").assertIsDisplayed()
    }
}
