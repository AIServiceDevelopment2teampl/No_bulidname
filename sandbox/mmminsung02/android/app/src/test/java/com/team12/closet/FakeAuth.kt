package com.team12.closet

import android.content.Context
import com.team12.closet.data.ApiResult
import com.team12.closet.data.AuthRepository
import com.team12.closet.data.GoogleSignIn
import com.team12.closet.data.GoogleSignInResult
import com.team12.closet.data.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 서버 대신 쓰는 가짜 로그인. 기본은 로그인된 상태 */
class FakeAuthRepository(
    loggedIn: Boolean = true,
) : AuthRepository {
    private val _session = MutableStateFlow(if (loggedIn) Session("test-token", "테스트", "test@example.com") else null)
    override val session: StateFlow<Session?> = _session

    var loginResult: ApiResult<Unit> = ApiResult.Ok(Unit)
    var deleteResult: ApiResult<Unit> = ApiResult.Ok(Unit)
    val loginTokens = mutableListOf<String>()
    var logoutCalls = 0
    var deleteCalls = 0

    override suspend fun loginWithGoogle(
        idToken: String,
        displayName: String?,
        email: String?,
    ): ApiResult<Unit> {
        loginTokens += idToken
        if (loginResult is ApiResult.Ok) _session.value = Session("session-for-$idToken", displayName, email)
        return loginResult
    }

    override suspend fun logout() {
        logoutCalls++
        _session.value = null
    }

    override suspend fun deleteAccount(): ApiResult<Unit> {
        deleteCalls++
        if (deleteResult is ApiResult.Ok) _session.value = null
        return deleteResult
    }
}

/** 계정 선택 창 대신 정해 둔 결과를 돌려준다 */
class FakeGoogleSignIn(
    var result: GoogleSignInResult = GoogleSignInResult.Success("google-id-token", "홍길동", "user@example.com"),
) : GoogleSignIn {
    var calls = 0

    override suspend fun signIn(activityContext: Context): GoogleSignInResult {
        calls++
        return result
    }
}
