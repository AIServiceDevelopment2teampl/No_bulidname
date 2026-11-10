package com.team12.closet.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException

/** 서버 POST /v1/products/{id}/similar 결과의 옷 한 벌 */
@Serializable
data class SimilarCloth(
    val cloth: Cloth,
    // 이미지 유사도. 화면에는 숫자를 보여 주지 않고 level만 쓴다 (2026-11-04 결정)
    @SerialName("image_similarity") val imageSimilarity: Double,
    val level: String,
)

@Serializable
data class SimilarResult(
    /** ok / empty_closet / no_similar */
    val status: String,
    val items: List<SimilarCloth>,
)

interface ProductsApi {
    @POST("v1/products/{id}/similar")
    suspend fun similar(
        @Path("id") productId: Long,
    ): Response<SimilarResult>
}

interface ProductsRepository {
    suspend fun findSimilar(productId: Long): ApiResult<SimilarResult>
}

class NetworkProductsRepository(
    private val api: ProductsApi,
) : ProductsRepository {
    override suspend fun findSimilar(productId: Long): ApiResult<SimilarResult> {
        val response =
            try {
                api.similar(productId)
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null) return ApiResult.Ok(body)
        return ApiResult.Error(
            when (response.code()) {
                400 -> "상품 사진을 찾지 못했어요"
                404 -> "상품을 찾을 수 없어요"
                else -> NetworkClothesRepository.TRY_LATER
            },
        )
    }
}
