package com.team12.closet

import com.team12.closet.data.AddShopResult
import com.team12.closet.data.MyShop
import com.team12.closet.data.Shop
import com.team12.closet.data.ShopUrls
import com.team12.closet.data.ShopsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** 서버·Room 대신 쓰는 가짜. 검사 규칙은 실제 DefaultShopsRepository와 같게 둔다 */
class FakeShopsRepository(
    private val defaults: List<Shop> = SAMPLE,
) : ShopsRepository {
    override val myShops = MutableStateFlow<List<MyShop>>(emptyList())

    override suspend fun defaultShops() = defaults

    override suspend fun addMyShop(
        name: String,
        url: String,
        shipping: String,
    ): AddShopResult {
        val clean = ShopUrls.normalize(url)
        if (name.isBlank()) return AddShopResult.Rejected("쇼핑몰 이름을 적어 주세요")
        if (!ShopUrls.isHttps(clean)) return AddShopResult.Rejected("https:// 로 시작하는 주소만 추가할 수 있어요")
        val known = (defaults.map { it.url } + myShops.value.map { it.url }).map(ShopUrls::key)
        if (ShopUrls.key(clean) in known) return AddShopResult.Rejected("이미 목록에 있는 쇼핑몰이에요")
        myShops.value = myShops.value + MyShop(clean, name.trim(), shipping)
        return AddShopResult.Added
    }

    override suspend fun removeMyShop(url: String) {
        myShops.value = myShops.value.filterNot { it.url == url }
    }

    companion object {
        val SAMPLE =
            listOf(
                Shop("musinsa", "무신사", "https://www.musinsa.com/", "domestic", "ko", true, "direct"),
                Shop("asos", "ASOS", "https://www.asos.com/", "overseas", "en", false, "direct"),
                Shop("zozotown", "ZOZOTOWN", "https://zozo.jp/", "overseas", "ja", false, "forwarding"),
                Shop("ssense", "SSENSE", "https://www.ssense.com/", "overseas", "en", true, "direct"),
                Shop("beams", "BEAMS", "https://www.beams.co.jp/", "overseas", "ja", false, "unknown"),
            )
    }
}
