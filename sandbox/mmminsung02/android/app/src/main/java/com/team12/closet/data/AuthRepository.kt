package com.team12.closet.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import java.io.IOException

@Serializable
data class GoogleLoginRequest(
    @SerialName("id_token") val idToken: String,
)

@Serializable
data class LoginResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("session_token") val sessionToken: String,
)

interface AuthApi {
    @POST("v1/auth/google")
    suspend fun loginWithGoogle(
        @Body body: GoogleLoginRequest,
    ): retrofit2.Response<LoginResponse>

    @POST("v1/auth/logout")
    suspend fun logout(): retrofit2.Response<Unit>

    @DELETE("v1/me")
    suspend fun deleteAccount(): retrofit2.Response<Unit>
}

interface AuthRepository {
    /** null이면 로그인하지 않은 상태. 앱은 로그인 화면을 보여 준다 */
    val session: StateFlow<Session?>

    suspend fun loginWithGoogle(
        idToken: String,
        displayName: String?,
        email: String?,
    ): ApiResult<Unit>

    /** 서버 없이 이 폰에서만 게스트 세션을 만든다. 서버 기능은 Google 로그인 전까지 쓸 수 없다 */
    fun loginAsGuest()

    suspend fun logout()

    suspend fun deleteAccount(): ApiResult<Unit>
}

/**
 * 세션 상태를 하나로 모은다. 저장소와 화면이 같은 값을 보도록 세션은 여기서만 바꾼다.
 * 서버가 401을 주면(만료·삭제된 세션) AuthInterceptor가 [expire]를 불러 로그인 화면으로 돌아간다
 */
class SessionHolder(
    private val store: SessionStore,
) {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** 서버로 보낼 세션 토큰. 게스트는 서버 계정이 없어 null */
    val token: String? get() = _session.value?.takeUnless { it.guest }?.token

    fun set(session: Session) {
        store.save(session)
        _session.value = session
    }

    fun expire() {
        store.clear()
        _session.value = null
    }
}

class AuthInterceptor(
    private val holder: SessionHolder,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = holder.token
        val request =
            if (token != null) {
                chain
                    .request()
                    .newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
        val response = chain.proceed(request)
        // 로그인 요청 자체의 401(잘못된 Google 토큰)은 세션과 상관없다
        if (response.code == 401 && token != null && !request.url.encodedPath.endsWith("/v1/auth/google")) {
            holder.expire()
        }
        return response
    }
}

class NetworkAuthRepository(
    private val api: AuthApi,
    private val holder: SessionHolder,
) : AuthRepository {
    override val session = holder.session

    override suspend fun loginWithGoogle(
        idToken: String,
        displayName: String?,
        email: String?,
    ): ApiResult<Unit> {
        val response =
            try {
                api.loginWithGoogle(GoogleLoginRequest(idToken))
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null) {
            holder.set(Session(body.sessionToken, displayName, email))
            return ApiResult.Ok(Unit)
        }
        return ApiResult.Error(
            when (response.code()) {
                401 -> "Google 로그인을 확인하지 못했어요. 다시 시도해 주세요"
                503 -> "서버에 Google 로그인 설정이 아직 없어요"
                else -> NetworkClothesRepository.TRY_LATER
            },
        )
    }

    override fun loginAsGuest() {
        holder.set(Session(GUEST_TOKEN, guest = true))
    }

    /** 서버에 알리지 못해도(오프라인) 이 폰에서는 로그아웃한다. 게스트는 서버에 알릴 게 없다 */
    override suspend fun logout() {
        if (holder.session.value?.guest != true) runCatching { api.logout() }
        holder.expire()
    }

    override suspend fun deleteAccount(): ApiResult<Unit> {
        if (holder.session.value?.guest == true) {
            holder.expire()
            return ApiResult.Ok(Unit)
        }
        val response =
            try {
                api.deleteAccount()
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        if (!response.isSuccessful) return ApiResult.Error(NetworkClothesRepository.TRY_LATER)
        holder.expire()
        return ApiResult.Ok(Unit)
    }
}
