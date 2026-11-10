package com.team12.closet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.team12.closet.data.ApiResult
import com.team12.closet.data.GoogleSignInResult
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.theme.InMemoryThemeStore
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** account/google-login.md 완료 조건 [앱] (서버·Google은 가짜) */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LoginFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val clothes = FakeClothesRepository()
    private val google = FakeGoogleSignIn()

    private fun start(auth: FakeAuthRepository) {
        composeRule.setContent { ClosetApp(clothes, InMemoryThemeStore(), auth, google, FakeShopsRepository()) }
    }

    @Test
    fun notLoggedInShowsLoginAndLoggingInGoesHome() {
        val auth = FakeAuthRepository(loggedIn = false)
        start(auth)
        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_HOME").assertDoesNotExist()

        composeRule.onNodeWithTag("login_google").performClick()

        assertEquals(listOf("google-id-token"), auth.loginTokens)
        composeRule.onNodeWithTag("login_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("screen_HOME").assertIsDisplayed()
    }

    @Test
    fun loggedInStartsAtHome() {
        start(FakeAuthRepository(loggedIn = true))

        composeRule.onNodeWithTag("screen_HOME").assertIsDisplayed()
        composeRule.onNodeWithTag("login_screen").assertDoesNotExist()
    }

    @Test
    fun cancellingAccountPickerStaysWithoutMessage() {
        google.result = GoogleSignInResult.Cancelled
        val auth = FakeAuthRepository(loggedIn = false)
        start(auth)

        composeRule.onNodeWithTag("login_google").performClick()

        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("login_message").assertDoesNotExist()
        assertEquals(emptyList<String>(), auth.loginTokens)
    }

    @Test
    fun missingClientIdShowsNotice() {
        google.result = GoogleSignInResult.NotConfigured
        start(FakeAuthRepository(loggedIn = false))

        composeRule.onNodeWithTag("login_google").performClick()

        composeRule.onNodeWithText("앱에 Google 로그인 설정이 아직 없어요").assertIsDisplayed()
    }

    @Test
    fun serverRejectingLoginShowsMessageAndStaysOnLogin() {
        val auth = FakeAuthRepository(loggedIn = false).apply { loginResult = ApiResult.Error("서버에 연결하지 못했어요") }
        start(auth)

        composeRule.onNodeWithTag("login_google").performClick()

        composeRule.onNodeWithText("서버에 연결하지 못했어요").assertIsDisplayed()
        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
    }

    @Test
    fun deletingAccountAfterConfirmGoesBackToLogin() {
        val auth = FakeAuthRepository(loggedIn = true)
        start(auth)
        composeRule.onNodeWithTag("tab_MY").performClick()

        composeRule.onNodeWithTag("delete_account").performScrollTo().performClick()
        composeRule.onNodeWithText("계정을 삭제할까요?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_account").performClick()

        assertEquals(1, auth.deleteCalls)
        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
    }

    @Test
    fun failedAccountDeletionStaysAndShowsMessage() {
        val auth = FakeAuthRepository(loggedIn = true).apply { deleteResult = ApiResult.Error("서버에 연결하지 못했어요") }
        start(auth)
        composeRule.onNodeWithTag("tab_MY").performClick()

        composeRule.onNodeWithTag("delete_account").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_delete_account").performClick()

        composeRule.onNodeWithText("서버에 연결하지 못했어요").assertIsDisplayed()
        composeRule.onNodeWithTag("login_screen").assertDoesNotExist()
    }

    @Test
    fun logoutAsksThenGoesToLoginAndCancelReturns() {
        val auth = FakeAuthRepository(loggedIn = true)
        start(auth)
        composeRule.onNodeWithTag("tab_MY").performClick()

        composeRule.onNodeWithTag("my_logout").performScrollTo().performClick()
        composeRule.onNodeWithText("정말 로그아웃하시겠습니까?").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_logout").performClick()
        composeRule.onNodeWithTag("my_logout").assertIsDisplayed()
        assertEquals(0, auth.logoutCalls)

        composeRule.onNodeWithTag("my_logout").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_logout").performClick()

        assertEquals(1, auth.logoutCalls)
        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
    }

    @Test
    fun myScreenShowsGoogleAccountNameAndEmail() {
        start(FakeAuthRepository(loggedIn = true))

        composeRule.onNodeWithTag("tab_MY").performClick()

        composeRule.onNodeWithText("테스트").assertIsDisplayed()
        composeRule.onNodeWithText("test@example.com · Google 계정").assertIsDisplayed()
    }

    // 검수에서 찾은 문제: 옷장 ViewModel이 로그인이 바뀌어도 남아 있어서,
    // 같은 폰에서 다른 사람이 로그인하면 새 목록을 불러오는 동안 앞사람 옷이 보였다
    @Test
    fun nextUserDoesNotSeePreviousUsersClothesWhileLoading() {
        clothes.clothes += FakeClothesRepository.cloth(1, "top", "black", "앞사람 옷")
        val auth = FakeAuthRepository(loggedIn = true)
        start(auth)
        composeRule.onNodeWithTag("tab_CLOSET").performClick()
        composeRule.onNodeWithTag("cloth_1").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_MY").performClick()
        composeRule.onNodeWithTag("my_logout").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_logout").performClick()

        // 다음 사람이 로그인. 이 사람의 목록 응답은 아직 오지 않았다
        clothes.holdNextList = CompletableDeferred()
        composeRule.onNodeWithTag("login_google").performClick()
        composeRule.onNodeWithTag("tab_CLOSET").performClick()

        composeRule.onNodeWithTag("cloth_1").assertDoesNotExist()
    }
}
