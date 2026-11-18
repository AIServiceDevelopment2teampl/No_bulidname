package com.team12.closet.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import java.io.IOException

/** 자동 등록 종류 (docs/features/auto-register 문서). 서버 imports.type 값 */
enum class ImportKind(
    val code: String,
) {
    ORDER_CAPTURE("order_capture"),
    RECEIPT("receipt"),
    IN_APP("in_app"),
}

/** 등록 후보 하나. 서버가 개인 정보·결제 정보 형식의 값은 이미 지워서 보낸다 */
@Serializable
data class ImportCandidate(
    val index: Int,
    val name: String,
    val option: String? = null,
    val color: String? = null,
    val size: String? = null,
    val price: String? = null,
    val date: String? = null,
    val category: String? = null,
    val material: String? = null,
    @SerialName("is_clothing") val isClothing: Boolean = true,
    val duplicate: Boolean = false,
    val selected: Boolean = true,
    @SerialName("has_image") val hasImage: Boolean = false,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class ImportBatch(
    val id: Long,
    val type: String,
    val status: String,
    val candidates: List<ImportCandidate>,
)

/** 확인 화면에서 고른 후보와 사용자가 고친 값 (null은 보내지 않는다 = 서버 값 그대로) */
@Serializable
data class ImportChoice(
    val index: Int,
    val category: String? = null,
    val color: String? = null,
    val name: String? = null,
)

@Serializable
data class ConfirmRequest(
    val items: List<ImportChoice>,
)

@Serializable
data class SkippedCandidate(
    val index: Int,
    val reason: String,
)

@Serializable
data class ConfirmResult(
    val saved: List<Cloth>,
    val skipped: List<SkippedCandidate> = emptyList(),
)

interface ImportsRepository {
    /** 주문내역 캡처(최대 5장) · 영수증(1장) → 등록 후보. 옷장에는 아직 저장하지 않는다 */
    suspend fun fromPhotos(
        kind: ImportKind,
        photos: List<PickedPhoto>,
    ): ApiResult<ImportBatch>

    /** orderJson: 주입 스크립트 order.js가 모은 값 그대로 (JSON 글자) */
    suspend fun fromOrder(orderJson: String): ApiResult<ImportBatch>

    suspend fun confirm(
        id: Long,
        choices: List<ImportChoice>,
    ): ApiResult<ConfirmResult>

    /** 확인 화면·카드를 닫음: 저장하지 않는다 */
    suspend fun discard(id: Long): ApiResult<Unit>

    fun imageUrl(path: String?): String?
}

interface ImportsApi {
    @Multipart
    @POST("v1/imports")
    suspend fun create(
        @Part("type") type: RequestBody,
        @Part images: List<MultipartBody.Part>,
    ): Response<ImportBatch>

    @POST("v1/imports/in-app")
    suspend fun inApp(
        @Body order: RequestBody,
    ): Response<ImportBatch>

    @POST("v1/imports/{id}/confirm")
    suspend fun confirm(
        @Path("id") id: Long,
        @Body body: ConfirmRequest,
    ): Response<ConfirmResult>

    @DELETE("v1/imports/{id}")
    suspend fun discard(
        @Path("id") id: Long,
    ): Response<Unit>
}

class NetworkImportsRepository(
    private val api: ImportsApi,
    private val baseUrl: String,
) : ImportsRepository {
    override suspend fun fromPhotos(
        kind: ImportKind,
        photos: List<PickedPhoto>,
    ) = call {
        val parts =
            photos.map { photo ->
                MultipartBody.Part.createFormData("images", photo.name, photo.bytes.toRequestBody(photo.mimeType.toMediaType()))
            }
        api.create(kind.code.toRequestBody("text/plain".toMediaType()), parts)
    }

    override suspend fun fromOrder(orderJson: String) = call { api.inApp(orderJson.toRequestBody("application/json".toMediaType())) }

    override suspend fun confirm(
        id: Long,
        choices: List<ImportChoice>,
    ) = call { api.confirm(id, ConfirmRequest(choices)) }

    override suspend fun discard(id: Long): ApiResult<Unit> {
        val response =
            try {
                api.discard(id)
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        return if (response.isSuccessful) ApiResult.Ok(Unit) else ApiResult.Error(message(response))
    }

    override fun imageUrl(path: String?): String? = path?.let { baseUrl.trimEnd('/') + it }

    private suspend fun <T : Any> call(request: suspend () -> Response<T>): ApiResult<T> {
        val response =
            try {
                request()
            } catch (e: IOException) {
                return ApiResult.Error(NetworkClothesRepository.NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null) return ApiResult.Ok(body)
        return ApiResult.Error(message(response))
    }

    /** 서버 안내 문구(주문내역이 잘 보이게 다시 캡처해 주세요 등)를 그대로 보여 준다 */
    private fun message(response: Response<*>): String {
        val detail =
            runCatching {
                NetworkClothesRepository.json.decodeFromString<ErrorResponse>(response.errorBody()!!.string()).detail
            }.getOrNull()
        return when (response.code()) {
            400, 409, 413, 422, 429 -> detail ?: NetworkClothesRepository.TRY_LATER
            404 -> "등록 기록을 찾을 수 없어요"
            503 -> "지금은 사진으로 등록할 수 없어요. 서버 설정을 기다리는 중이에요"
            else -> NetworkClothesRepository.TRY_LATER
        }
    }
}
