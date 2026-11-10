package com.team12.closet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.team12.closet.FakeClothesRepository.Companion.cloth
import com.team12.closet.data.ClothFilter
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.theme.InMemoryThemeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * wardrobe/browse.md 완료 조건 [앱]과 예외 상황 (서버는 가짜).
 * 기본 Robolectric 화면은 작아서 상세 화면 아래쪽이 잘린다. 흔한 폰 크기로 맞춘다
 */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class ClosetScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository =
        FakeClothesRepository(
            listOf(
                cloth(1, "outer", "beige", "카멜 코트"),
                cloth(2, "top", "black", confirmed = false),
                cloth(3, "top", "white", "줄무늬 티"),
                cloth(4, "bottom", "blue", "청바지"),
                cloth(5, "top", "gray", "울 니트 베스트", needsPhoto = true),
            ),
        )

    private fun openCloset(repo: FakeClothesRepository = repository) {
        composeRule.setContent {
            ClosetApp(repo, InMemoryThemeStore(), FakeAuthRepository(), FakeGoogleSignIn(), FakeShopsRepository(), FakeProductsRepository())
        }
        composeRule.onNodeWithTag("tab_CLOSET").performClick()
    }

    private fun assertShown(vararg ids: Long) {
        ids.forEach { composeRule.onNodeWithTag("cloth_$it").assertIsDisplayed() }
    }

    private fun assertHidden(vararg ids: Long) {
        ids.forEach { composeRule.onNodeWithTag("cloth_$it").assertDoesNotExist() }
    }

    @Test
    fun changingCategoryFilterChangesList() {
        openCloset()
        assertShown(1, 2, 3, 4, 5)

        composeRule.onNodeWithTag("category_top").performScrollTo().performClick()

        assertShown(2, 3, 5)
        assertHidden(1, 4)
        assertEquals(ClothFilter(category = "top"), repository.listRequests.last())
    }

    @Test
    fun changingColorFilterChangesListAndTappingAgainClearsIt() {
        openCloset()

        composeRule.onNodeWithTag("color_black").performScrollTo().performClick()
        assertShown(2)
        assertHidden(1, 3, 4, 5)

        composeRule.onNodeWithTag("color_black").performScrollTo().performClick()
        assertShown(1, 2, 3, 4, 5)
    }

    @Test
    fun categoryAndColorFiltersCombineAndAllChipResets() {
        openCloset()

        composeRule.onNodeWithTag("category_top").performScrollTo().performClick()
        composeRule.onNodeWithTag("color_white").performScrollTo().performClick()
        assertShown(3)
        assertHidden(1, 2, 4, 5)

        composeRule.onNodeWithTag("category_all").performScrollTo().performClick()
        assertEquals(ClothFilter(color = "white"), repository.listRequests.last())
    }

    @Test
    fun showsCountBadgesAndNamesFallbackToCategoryAndColor() {
        openCloset()

        composeRule.onNodeWithTag("closet_count").assertTextEquals("  5벌")
        composeRule.onNodeWithText("미확인").assertIsDisplayed()
        composeRule.onNodeWithText("사진 필요").assertIsDisplayed()
        composeRule.onNodeWithText("상의 · 블랙").assertIsDisplayed()
        composeRule.onNodeWithText("확인이 필요한 옷 1벌 · 사진이 필요한 옷 1벌").assertIsDisplayed()
    }

    @Test
    fun emptyClosetShowsGuide() {
        openCloset(FakeClothesRepository())

        composeRule.onNodeWithText("아직 옷이 없어요. 주문내역 캡처로 한 번에 등록해 보세요").assertIsDisplayed()
    }

    @Test
    fun noMatchShowsNoResultMessage() {
        openCloset()

        composeRule.onNodeWithTag("color_red").performScrollTo().performClick()

        composeRule.onNodeWithText("조건에 맞는 옷이 없어요").assertIsDisplayed()
    }

    @Test
    fun loadFailureShowsRetry() {
        repository.failNextList = "서버에 연결하지 못했어요"
        openCloset()
        composeRule.onNodeWithText("서버에 연결하지 못했어요").assertIsDisplayed()

        composeRule.onNodeWithTag("closet_retry").performClick()

        assertShown(1, 2)
    }

    @Test
    fun editingClothSavesAndConfirms() {
        openCloset()
        composeRule.onNodeWithTag("cloth_2").performClick()

        composeRule.onNodeWithTag("category_outer").performScrollTo().performClick()
        composeRule.onNodeWithTag("color_navy").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_name").performScrollTo().performTextReplacement("검은 패딩")
        composeRule.onNodeWithTag("detail_save").performClick()

        val saved = repository.clothes.first { it.id == 2L }
        assertEquals("outer", saved.category)
        assertEquals("navy", saved.color)
        assertEquals("검은 패딩", saved.name)
        assertEquals(true, saved.confirmed)
        composeRule.onNodeWithTag("closet_grid").assertIsDisplayed()
        composeRule.onNodeWithText("검은 패딩").assertIsDisplayed()
    }

    @Test
    fun clearingTextFieldSavesAsUnknown() {
        openCloset()
        composeRule.onNodeWithTag("cloth_3").performClick()

        composeRule.onNodeWithTag("field_name").performScrollTo().performTextReplacement("")
        composeRule.onNodeWithTag("detail_save").performClick()

        assertNull(repository.clothes.first { it.id == 3L }.name)
    }

    @Test
    fun deletingClothAsksThenRemovesIt() {
        openCloset()
        composeRule.onNodeWithTag("cloth_4").performClick()

        composeRule.onNodeWithTag("detail_delete").performClick()
        composeRule.onNodeWithText("이 옷을 지울까요?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete").performClick()

        assertHidden(4)
        assertEquals(listOf(1L, 2L, 3L, 5L), repository.clothes.map { it.id })
    }

    @Test
    fun backFromDetailDoesNotSave() {
        openCloset()
        composeRule.onNodeWithTag("cloth_2").performClick()
        composeRule.onNodeWithTag("category_bottom").performScrollTo().performClick()

        composeRule.onNodeWithTag("detail_back").performClick()

        assertEquals("top", repository.clothes.first { it.id == 2L }.category)
        assertShown(2)
    }

    @Test
    fun deleteAllDataFromMyTabAfterConfirm() {
        openCloset()
        composeRule.onNodeWithTag("tab_MY").performClick()

        composeRule.onNodeWithTag("delete_all_data").performClick()
        composeRule.onNodeWithTag("confirm_delete_all").performClick()

        assertEquals(1, repository.deleteAllCalls)
        composeRule.onNodeWithText("모두 지웠어요").assertIsDisplayed()
    }

    // 검수에서 찾은 버그: 옷장 목록을 처음 한 번만 불러와서, 전체 삭제 뒤 돌아오면 지운 옷이 보였다
    @Test
    fun closetReloadsWhenComingBackAfterDeleteAll() {
        openCloset()
        assertShown(1, 2)
        composeRule.onNodeWithTag("tab_MY").performClick()
        composeRule.onNodeWithTag("delete_all_data").performClick()
        composeRule.onNodeWithTag("confirm_delete_all").performClick()

        composeRule.onNodeWithTag("tab_CLOSET").performClick()

        assertHidden(1, 2)
        composeRule.onNodeWithTag("closet_empty").assertIsDisplayed()
    }
}
