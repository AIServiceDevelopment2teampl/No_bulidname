package com.team12.closet.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException

/** AI 점원 대화 한 줄 (compare/ai-clerk.md) */
@Serializable
data class ClerkMessage(
    val id: Long,
    /** user / assistant */
    val role: String,
    val content: String,
)

@Serializable
data class ClerkQuestion(
    /** null이면 상품 페이지가 아닌 일반 대화 */
    @SerialName("product_id") val productId: Long?,
    val text: String,
)

@Serializable
data class ClerkReply(
    val message: ClerkMessage,
)

@Serializable
data class ClerkHistory(
    val items: List<ClerkMessage>,
)

@Serializable
data class MatchingCloth(
    val cloth: Cloth,
    val reason: String = "",
)

/** 이 상품과 어울리는 내 옷 (서버가 내 옷장에 있는 옷만, 같은 종류는 빼고 최대 3벌로 거른다) */
@Serializable
data class MatchingResult(
    /** ok / empty_closet / none */
    val status: String,
    val items: List<MatchingCloth> = emptyList(),
    val message: String? = null,
)

interface ClerkApi {
    @POST("v1/clerk/messages")
    suspend fun ask(
        @Body body: ClerkQuestion,
    ): Response<ClerkReply>

    @GET("v1/clerk/messages")
    suspend fun history(
        @Query("product_id") productId: Long?,
    ): Response<ClerkHistory>

    @POST("v1/products/{id}/matching")
    suspend fun matching(
        @Path("id") productId: Long,
    ): Response<MatchingResult>
}

interface ClerkRepository {
    suspend fun history(productId: Long?): ApiResult<List<ClerkMessage>>

    suspend fun ask(
        productId: Long?,
        text: String,
    ): ApiResult<ClerkMessage>

    suspend fun matching(productId: Long): ApiResult<MatchingResult>
}

class NetworkClerkRepository(
    private val api: ClerkApi,
) : ClerkRepository {
    override suspend fun history(productId: Long?) = call("대화를 불러오지 못했어요") { api.history(productId) }.map { it.items }

    override suspend fun ask(
        productId: Long?,
        text: String,
    ) = call("답하지 못했어요. 잠시 후 다시 시도해 주세요") { api.ask(ClerkQuestion(productId, text)) }.map { it.message }

    override suspend fun matching(productId: Long) = call("어울리는 옷을 고르지 못했어요") { api.matching(productId) }

    private suspend fun <T> call(
        fallback: String,
        request: suspend () -> Response<T>,
    ): ApiResult<T> {
        val response =
            try {
                request()
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null) return ApiResult.Ok(body)
        val detail =
            runCatching {
                NetworkClothesRepository.json.decodeFromString<ErrorResponse>(response.errorBody()!!.string()).detail
            }.getOrNull()
        return ApiResult.Error(detail ?: fallback)
    }

    private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
        when (this) {
            is ApiResult.Ok -> ApiResult.Ok(transform(value))
            is ApiResult.Error -> this
        }
}
