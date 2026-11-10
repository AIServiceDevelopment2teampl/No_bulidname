package com.team12.closet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.team12.closet.FakeClothesRepository.Companion.cloth
import com.team12.closet.data.SimilarCloth
import com.team12.closet.data.SimilarResult
import com.team12.closet.ui.compare.SimilarClothesSection
import com.team12.closet.ui.theme.ClosetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** compare/similar-search.md 완료 조건 [앱]: 점수 숫자(%) 없이 단계로만 보인다 */
@RunWith(RobolectricTestRunner::class)
class SimilarClothesSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(result: SimilarResult) {
        composeRule.setContent { ClosetTheme(darkTheme = false) { SimilarClothesSection(result, imageUrl = { null }) } }
    }

    private val threeResults =
        SimilarResult(
            "ok",
            listOf(
                SimilarCloth(cloth(1, "outer", "beige", "카멜 코트"), 0.9512, "very_similar"),
                SimilarCloth(cloth(2, "outer", "brown", "울 코트"), 0.8437, "similar"),
                SimilarCloth(cloth(3, "outer", "black", "패딩"), 0.6125, "different"),
            ),
        )

    @Test
    fun showsLevelsButNeverScoreNumbersOrPercent() {
        show(threeResults)

        composeRule.onNodeWithText("매우 비슷").assertIsDisplayed()
        composeRule.onNodeWithText("비슷").assertIsDisplayed()
        composeRule.onNodeWithText("조금 다름").assertIsDisplayed()
        for (forbidden in listOf("%", "0.95", "0.84", "0.61", "95", "84", "61", "확률")) {
            composeRule.onAllNodes(hasText(forbidden, substring = true)).assertCountEquals(0)
        }
    }

    @Test
    fun headlineCountsOnlySimilarOnes() {
        show(threeResults)

        composeRule.onNodeWithText("비슷한 옷 2벌 있어요").assertIsDisplayed()
        composeRule.onNodeWithText("내 옷장에서 찾았어요 (이미지 기준)").assertIsDisplayed()
        composeRule.onNodeWithTag("similar_3").assertIsDisplayed()
    }

    @Test
    fun noSimilarSaysSoAndStillShowsCandidates() {
        show(SimilarResult("no_similar", listOf(SimilarCloth(cloth(9, "top", "white", "흰 셔츠"), 0.41, "different"))))

        composeRule.onNodeWithText("비슷한 옷은 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("조금 다름").assertIsDisplayed()
        composeRule.onAllNodesWithText("매우 비슷").assertCountEquals(0)
    }

    @Test
    fun emptyClosetGuidesToRegister() {
        show(SimilarResult("empty_closet", emptyList()))

        composeRule.onNodeWithText("비교할 옷이 아직 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("옷을 등록하면 내 옷장에서 비슷한 옷을 찾아 드려요").assertIsDisplayed()
    }
}
