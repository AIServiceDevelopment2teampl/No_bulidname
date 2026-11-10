package com.team12.closet.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import java.io.IOException
import java.net.URI

/** 서버 GET /v1/shops 의 쇼핑몰 하나 (sandbox/mmminsung02/src/server/shared/shops.json) */
@Serializable
data class Shop(
    val id: String,
    val name: String,
    val url: String,
    /** domestic / overseas */
    val region: String,
    /** ko / en / ja */
    val lang: String,
    @SerialName("korean_page") val koreanPage: Boolean,
    /** direct / forwarding / unknown */
    val shipping: String,
)

@Serializable
data class ShopList(
    val shops: List<Shop>,
)

/** 목록 한 줄에 보여 줄 값 (기본 쇼핑몰과 내 쇼핑몰 공통) */
data class ShopRow(
    val name: String,
    val url: String,
    val detail: String,
    val tag: ShopTag,
    val mine: Boolean,
    val needsTranslation: Boolean,
    val domestic: Boolean,
)

enum class ShopTag(
    val label: String,
) {
    DOMESTIC("국내"),
    DIRECT("배송 가능"),
    FORWARDING("배송대행 필요"),
    UNKNOWN("그 외"),
    ;

    companion object {
        fun of(
            region: String,
            shipping: String,
        ) = when {
            region == "domestic" -> DOMESTIC
            shipping == "direct" -> DIRECT
            shipping == "forwarding" -> FORWARDING
            else -> UNKNOWN
        }

        /** 내 쇼핑몰 추가 때 고를 수 있는 값 */
        val choices = listOf("direct" to DIRECT, "forwarding" to FORWARDING, "unknown" to UNKNOWN)
    }
}

object ShopUrls {
    /** 'asos.com'처럼 쓰면 https://를 붙인다. http:// 등 다른 방식은 그대로 둔다 (검사에서 걸러진다) */
    fun normalize(input: String): String {
        val text = input.trim()
        return if ("://" in text) text else "https://$text"
    }

    fun host(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

    /** 같은 쇼핑몰인지 비교할 때 쓴다: 대소문자, 끝의 /, www. 차이는 무시 */
    fun key(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url.trim().lowercase()
        val host = (uri.host ?: "").lowercase().removePrefix("www.")
        val path = (uri.path ?: "").trimEnd('/').lowercase()
        return host + path
    }

    fun isHttps(url: String): Boolean =
        runCatching { URI(url).let { it.scheme == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false)
}

fun Shop.toRow(): ShopRow {
    val langName = if (lang == "ja") "일본어" else "영어"
    val detail =
        when {
            lang == "ko" -> "한국어"
            koreanPage -> "한국어 페이지 있음"
            else -> "$langName → 번역"
        }
    return ShopRow(
        name = name,
        url = url,
        detail = "${ShopUrls.host(url)} · $detail",
        tag = ShopTag.of(region, shipping),
        mine = false,
        needsTranslation = lang != "ko" && !koreanPage,
        domestic = region == "domestic",
    )
}

fun MyShop.toRow() =
    ShopRow(
        name = name,
        url = url,
        detail = "${ShopUrls.host(url)} · 내가 추가",
        tag = ShopTag.of("overseas", shipping),
        mine = true,
        needsTranslation = false,
        domestic = false,
    )

sealed interface AddShopResult {
    data object Added : AddShopResult

    data class Rejected(
        val message: String,
    ) : AddShopResult
}

interface ShopsApi {
    @GET("v1/shops")
    suspend fun shops(): Response<ShopList>
}

interface ShopsRepository {
    /** 서버 목록. 서버에 닿지 않으면 앱에 넣어 둔 기본 목록 */
    suspend fun defaultShops(): List<Shop>

    val myShops: Flow<List<MyShop>>

    suspend fun addMyShop(
        name: String,
        url: String,
        shipping: String,
    ): AddShopResult

    suspend fun removeMyShop(url: String)
}

class DefaultShopsRepository(
    private val api: ShopsApi,
    private val dao: MyShopDao,
    private val builtIn: () -> List<Shop>,
) : ShopsRepository {
    private var lastDefaults: List<Shop>? = null

    override suspend fun defaultShops(): List<Shop> {
        val fromServer =
            try {
                api
                    .shops()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.shops
            } catch (e: IOException) {
                null
            }
        return (fromServer ?: builtIn()).also { lastDefaults = it }
    }

    override val myShops: Flow<List<MyShop>> = dao.observeAll()

    override suspend fun addMyShop(
        name: String,
        url: String,
        shipping: String,
    ): AddShopResult {
        val cleanName = name.trim()
        val cleanUrl = ShopUrls.normalize(url)
        if (cleanName.isEmpty()) return AddShopResult.Rejected("쇼핑몰 이름을 적어 주세요")
        if (!ShopUrls.isHttps(cleanUrl)) return AddShopResult.Rejected("https:// 로 시작하는 주소만 추가할 수 있어요")
        val key = ShopUrls.key(cleanUrl)
        val known = (lastDefaults ?: builtIn()).map { ShopUrls.key(it.url) } + dao.all().map { ShopUrls.key(it.url) }
        if (key in known) return AddShopResult.Rejected("이미 목록에 있는 쇼핑몰이에요")
        dao.insert(MyShop(url = cleanUrl, name = cleanName, shipping = shipping))
        return AddShopResult.Added
    }

    override suspend fun removeMyShop(url: String) = dao.delete(url)

    companion object {
        /** 앱 assets의 shops.json (서버와 같은 파일, build.gradle.kts의 sourceSets 참고) */
        fun builtInFrom(context: Context): () -> List<Shop> =
            {
                context.assets.open("shops.json").use {
                    NetworkClothesRepository.json.decodeFromString<ShopList>(it.readBytes().decodeToString()).shops
                }
            }
    }
}
