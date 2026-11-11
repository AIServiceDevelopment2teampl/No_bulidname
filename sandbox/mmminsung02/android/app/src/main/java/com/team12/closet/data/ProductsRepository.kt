package com.team12.closet.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
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
    /** 스크린샷에서 흐려서 확실히 읽지 못한 글자 (화면에 '불확실') */
    val uncertain: Boolean = false,
)

/** 서버 POST /v1/products/extract 결과 */
@Serializable
data class ProductInfo(
    val id: Long,
    /** 공유받은 스크린샷 상품은 주소가 없다 */
    val url: String? = null,
    val site: String? = null,
    val lang: String,
    val category: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("has_image") val hasImage: Boolean,
    /** 서버에 저장한 상품 사진 (스크린샷에서 잘라 낸 것) */
    @SerialName("image_path") val imagePath: String? = null,
    val fields: Map<String, ProductField?>,
) {
    fun field(key: String): ProductField? = fields[key]
}

/** 비교 리포트 한 줄. 근거 필드명과 양쪽 값. text는 LLM 문장 (실패하면 null → 비교표로 보여 줌) */
@Serializable
data class ReportItem(
    val field: String,
    val label: String,
    val product: String? = null,
    val cloth: String? = null,
    /** 색의 근거: page(쇼핑몰에 적힘) / photo(상품 사진에서 읽음) */
    val basis: String? = null,
    val text: String? = null,
)

/** 서버 POST /v1/products/{id}/report 결과 (compare/purchase-report.md) */
@Serializable
data class PurchaseReport(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    /** ok / no_similar */
    val status: String,
    val headline: String,
    /** 비교한 내 옷 (가장 비슷한 1벌). 비슷한 옷이 없으면 null */
    val cloth: Cloth? = null,
    val level: String? = null,
    val summary: String? = null,
    val similar: List<ReportItem> = emptyList(),
    val different: List<ReportItem> = emptyList(),
    val unknown: List<ReportItem> = emptyList(),
)

/** 서버 POST /v1/products/from-screenshot 결과 (shopping/share-intake.md) */
@Serializable
data class ScreenshotResult(
    val product: ProductInfo,
    /** 옷 사진을 찾지 못하면 null (notice에 이유) */
    val similar: SimilarResult? = null,
    val report: PurchaseReport? = null,
    val notice: String? = null,
)

interface ProductsApi {
    @POST("v1/products/extract")
    suspend fun extract(
        @Body page: RequestBody,
    ): Response<ProductInfo>

    @POST("v1/products/{id}/similar")
    suspend fun similar(
        @Path("id") productId: Long,
    ): Response<SimilarResult>

    @Multipart
    @POST("v1/products/from-screenshot")
    suspend fun fromScreenshot(
        @Part image: MultipartBody.Part,
    ): Response<ScreenshotResult>

    @POST("v1/products/{id}/report")
    suspend fun report(
        @Path("id") productId: Long,
    ): Response<PurchaseReport>
}

interface ProductsRepository {
    /** pageJson: 주입 스크립트 product.js가 모은 값 그대로 (JSON 글자) */
    suspend fun extract(pageJson: String): ApiResult<ProductInfo>

    suspend fun findSimilar(productId: Long): ApiResult<SimilarResult>

    suspend fun report(productId: Long): ApiResult<PurchaseReport>

    /** 공유받은 스크린샷 한 장 → 상품 정보 + 비슷한 옷 + 리포트 */
    suspend fun fromScreenshot(photo: PickedPhoto): ApiResult<ScreenshotResult>
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

    override suspend fun report(productId: Long): ApiResult<PurchaseReport> {
        val response =
            try {
                api.report(productId)
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null) return ApiResult.Ok(body)
        return ApiResult.Error("비교 리포트를 만들지 못했어요")
    }

    override suspend fun fromScreenshot(photo: PickedPhoto): ApiResult<ScreenshotResult> {
        val part = MultipartBody.Part.createFormData("image", photo.name, photo.bytes.toRequestBody(photo.mimeType.toMediaType()))
        val response =
            try {
                api.fromScreenshot(part)
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null) return ApiResult.Ok(body)
        // 400·413·422·429는 서버가 사용자에게 보여 줄 문장을 준다
        val detail =
            if (response.code() in setOf(400, 413, 422, 429)) {
                runCatching { errorJson.decodeFromString<ErrorResponse>(response.errorBody()!!.string()).detail }.getOrNull()
            } else {
                null
            }
        return ApiResult.Error(detail ?: SCREENSHOT_FAILED)
    }

    companion object {
        const val SCREENSHOT_FAILED = "스크린샷을 읽지 못했어요. 잠시 후 다시 시도해 주세요"
        private val errorJson = Json { ignoreUnknownKeys = true }
    }
}
