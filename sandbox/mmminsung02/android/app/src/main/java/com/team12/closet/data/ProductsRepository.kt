package com.team12.closet.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
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

/** 상품 정보 한 칸. 원문에 없던 칸은 서버가 null로 준다 (= 정보 없음) */
@Serializable
data class ProductField(
    val original: String,
    val ko: String,
    /** 치수에 단위 변환을 붙인 값 (예: 81.3 cm (32 in)) */
    val converted: String? = null,
)

/** 서버 POST /v1/products/extract 결과 */
@Serializable
data class ProductInfo(
    val id: Long,
    val url: String,
    val site: String? = null,
    val lang: String,
    val category: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("has_image") val hasImage: Boolean,
    val fields: Map<String, ProductField?>,
) {
    fun field(key: String): ProductField? = fields[key]
}

interface ProductsApi {
    @POST("v1/products/extract")
    suspend fun extract(
        @Body page: RequestBody,
    ): Response<ProductInfo>

    @POST("v1/products/{id}/similar")
    suspend fun similar(
        @Path("id") productId: Long,
    ): Response<SimilarResult>
}

interface ProductsRepository {
    /** pageJson: 주입 스크립트 product.js가 모은 값 그대로 (JSON 글자) */
    suspend fun extract(pageJson: String): ApiResult<ProductInfo>

    suspend fun findSimilar(productId: Long): ApiResult<SimilarResult>
}

class NetworkProductsRepository(
    private val api: ProductsApi,
) : ProductsRepository {
    override suspend fun extract(pageJson: String): ApiResult<ProductInfo> {
        val response =
            try {
                // 페이지 데이터(JSON-LD 등)는 앱에서 해석하지 않고 서버로 그대로 넘긴다
                api.extract(pageJson.toRequestBody("application/json".toMediaType()))
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null) return ApiResult.Ok(body)
        return ApiResult.Error("상품 정보를 읽지 못했어요")
    }

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
