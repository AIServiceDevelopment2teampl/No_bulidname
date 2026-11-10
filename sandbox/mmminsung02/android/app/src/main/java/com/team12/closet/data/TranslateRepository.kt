package com.team12.closet.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException

@Serializable
data class TranslateRequest(
    val texts: List<String>,
)

@Serializable
data class TranslateResponse(
    val translations: List<String>,
)

interface TranslateApi {
    @POST("v1/translate")
    suspend fun translate(
        @Body body: TranslateRequest,
    ): Response<TranslateResponse>
}

interface TranslateRepository {
    /** 같은 길이·순서의 한국어. 실패하면 Error (화면은 원문 그대로) */
    suspend fun translate(texts: List<String>): ApiResult<List<String>>
}

class NetworkTranslateRepository(
    private val api: TranslateApi,
) : TranslateRepository {
    override suspend fun translate(texts: List<String>): ApiResult<List<String>> {
        val response =
            try {
                api.translate(TranslateRequest(texts))
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null && body.translations.size == texts.size) {
            return ApiResult.Ok(body.translations)
        }
        return ApiResult.Error("번역하지 못했어요")
    }
}
