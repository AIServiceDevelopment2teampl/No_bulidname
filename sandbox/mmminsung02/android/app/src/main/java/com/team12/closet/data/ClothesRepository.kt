package com.team12.closet.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

interface ClothesRepository {
    suspend fun uploadPhoto(photo: PickedPhoto): UploadOutcome

    suspend fun listClothes(filter: ClothFilter): ApiResult<ClothesPage>

    suspend fun updateCloth(
        id: Long,
        update: ClothUpdate,
    ): ApiResult<Cloth>

    suspend fun deleteCloth(id: Long): ApiResult<Unit>

    suspend fun deleteAllMyData(): ApiResult<Unit>

    /** 서버가 준 상대 주소(/v1/clothes/1/image)를 이미지 로더가 쓸 전체 주소로 */
    fun imageUrl(path: String?): String?
}

interface ClothesApi {
    @Multipart
    @POST("v1/clothes")
    suspend fun upload(
        @Part image: MultipartBody.Part,
    ): Response<UploadResponse>

    @GET("v1/clothes")
    suspend fun list(
        @Query("category") category: String?,
        @Query("color") color: String?,
    ): Response<ClothesPage>

    @PATCH("v1/clothes/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body update: ClothUpdate,
    ): Response<Cloth>

    @DELETE("v1/clothes/{id}")
    suspend fun delete(
        @Path("id") id: Long,
    ): Response<Unit>

    @DELETE("v1/me/data")
    suspend fun deleteMyData(): Response<Unit>
}

class NetworkClothesRepository(
    private val api: ClothesApi,
    private val baseUrl: String,
) : ClothesRepository {
    override suspend fun uploadPhoto(photo: PickedPhoto): UploadOutcome {
        val body = photo.bytes.toRequestBody(photo.mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("image", photo.name, body)
        val response =
            try {
                api.upload(part)
            } catch (e: IOException) {
                return UploadOutcome.Failed(NO_CONNECTION)
            }

        val result = response.body()
        if (response.isSuccessful && result != null) {
            return UploadOutcome.Registered(
                cloth =
                    RegisteredCloth(
                        id = result.id,
                        category = result.category,
                        color = result.color,
                        confirmed = result.confirmed,
                        needsCategory = result.needsCategory,
                    ),
                duplicate = result.duplicate,
            )
        }
        return when (response.code()) {
            400, 413 -> UploadOutcome.Rejected(errorDetail(response) ?: "사진을 등록할 수 없어요")
            else -> UploadOutcome.Failed(TRY_LATER)
        }
    }

    override suspend fun listClothes(filter: ClothFilter) = call { api.list(filter.category, filter.color) }

    override suspend fun updateCloth(
        id: Long,
        update: ClothUpdate,
    ) = call { api.update(id, update) }

    override suspend fun deleteCloth(id: Long) = callNoBody { api.delete(id) }

    override suspend fun deleteAllMyData() = callNoBody { api.deleteMyData() }

    override fun imageUrl(path: String?): String? = path?.let { baseUrl.trimEnd('/') + it }

    private suspend fun <T : Any> call(request: suspend () -> Response<T>): ApiResult<T> {
        val response =
            try {
                request()
            } catch (e: IOException) {
                return ApiResult.Error(NO_CONNECTION)
            }
        val body = response.body()
        if (response.isSuccessful && body != null) return ApiResult.Ok(body)
        return ApiResult.Error(errorMessage(response))
    }

    private suspend fun callNoBody(request: suspend () -> Response<Unit>): ApiResult<Unit> {
        val response =
            try {
                request()
            } catch (e: IOException) {
                return ApiResult.Error(NO_CONNECTION)
            }
        return if (response.isSuccessful) ApiResult.Ok(Unit) else ApiResult.Error(errorMessage(response))
    }

    private fun errorMessage(response: Response<*>): String =
        when (response.code()) {
            404 -> "옷을 찾을 수 없어요"
            400 -> errorDetail(response) ?: "값을 확인해 주세요"
            else -> TRY_LATER
        }

    private fun errorDetail(response: Response<*>): String? =
        runCatching {
            json.decodeFromString<ErrorResponse>(response.errorBody()!!.string()).detail
        }.getOrNull()

    companion object {
        const val NO_CONNECTION = "서버에 연결하지 못했어요"
        const val TRY_LATER = "잠시 후 다시 시도해 주세요"

        // null인 값은 JSON에 넣지 않는다 (서버는 보낸 값만 바꾼다)
        val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

        fun create(baseUrl: String): NetworkClothesRepository {
            val client =
                OkHttpClient
                    .Builder()
                    // 서버가 처음 켜질 때 CLIP을 올리느라 느릴 수 있다
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            val retrofit =
                Retrofit
                    .Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
            return NetworkClothesRepository(retrofit.create(ClothesApi::class.java), baseUrl)
        }
    }
}
