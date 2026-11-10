package com.team12.closet.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 서버 연결 하나. API와 옷 사진 불러오기(Coil)가 같은 OkHttpClient를 써서
 * 모든 요청에 로그인 세션이 붙는다
 */
class ServerConnection(
    baseUrl: String,
    sessionStore: SessionStore,
) {
    val shopsApi: ShopsApi by lazy { retrofit.create(ShopsApi::class.java) }

    val sessions = SessionHolder(sessionStore)

    val httpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(AuthInterceptor(sessions))
            // 서버가 처음 켜질 때 CLIP을 올리느라 느릴 수 있다
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    private val retrofit: Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(NetworkClothesRepository.json.asConverterFactory("application/json".toMediaType()))
            .build()

    val clothes = NetworkClothesRepository(retrofit.create(ClothesApi::class.java), baseUrl)
    val auth = NetworkAuthRepository(retrofit.create(AuthApi::class.java), sessions)
    val products = NetworkProductsRepository(retrofit.create(ProductsApi::class.java))
}
