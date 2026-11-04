package com.team12.closet.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.IOException
import java.util.concurrent.TimeUnit

interface ClothesRepository {
    suspend fun uploadPhoto(photo: PickedPhoto): UploadOutcome
}

interface ClothesApi {
    @Multipart
    @POST("v1/clothes")
    suspend fun upload(
        @Part image: MultipartBody.Part,
    ): Response<UploadResponse>
}

class NetworkClothesRepository(
    private val api: ClothesApi,
) : ClothesRepository {
    override suspend fun uploadPhoto(photo: PickedPhoto): UploadOutcome {
        val body = photo.bytes.toRequestBody(photo.mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("image", photo.name, body)
        val response =
            try {
                api.upload(part)
            } catch (e: IOException) {
                return UploadOutcome.Failed("서버에 연결하지 못했어요")
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
            else -> UploadOutcome.Failed("잠시 후 다시 시도해 주세요")
        }
    }

    private fun errorDetail(response: Response<*>): String? =
        runCatching {
            json.decodeFromString<ErrorResponse>(response.errorBody()!!.string()).detail
        }.getOrNull()

    companion object {
        val json = Json { ignoreUnknownKeys = true }

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
            return NetworkClothesRepository(retrofit.create(ClothesApi::class.java))
        }
    }
}
