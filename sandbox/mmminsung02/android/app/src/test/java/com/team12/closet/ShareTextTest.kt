package com.team12.closet

import com.team12.closet.data.ShareText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** shopping/share-intake.md: 공유받은 글에서 URL 뽑기 (단위 테스트) */
class ShareTextTest {
    @Test
    fun findsUrlInsideSharedText() {
        assertEquals(
            "https://zozo.jp/shop/beams/goods/7781/",
            ShareText.findUrl("ウールカーディガン | ZOZOTOWN https://zozo.jp/shop/beams/goods/7781/ #ZOZOTOWN"),
        )
    }

    @Test
    fun keepsQueryButDropsTrailingPunctuation() {
        assertEquals("https://www.asos.com/p/1?clr=camel&size=8", ShareText.findUrl("Look: https://www.asos.com/p/1?clr=camel&size=8."))
        assertEquals("https://zozo.jp/x/", ShareText.findUrl("（https://zozo.jp/x/）"))
        assertEquals("https://zozo.jp/x/", ShareText.findUrl("見て https://zozo.jp/x/。"))
        assertEquals("https://a.example/p", ShareText.findUrl("(https://a.example/p)"))
    }

    @Test
    fun httpIsOpenedAsHttps() {
        assertEquals("https://www.musinsa.com/products/1", ShareText.findUrl("http://www.musinsa.com/products/1"))
    }

    @Test
    fun firstUrlWins() {
        assertEquals("https://a.example/1", ShareText.findUrl("https://a.example/1 and https://b.example/2"))
    }

    @Test
    fun noUrlGivesNull() {
        assertNull(ShareText.findUrl("이 옷 예쁘다"))
        assertNull(ShareText.findUrl(""))
        assertNull(ShareText.findUrl(null))
        assertNull(ShareText.findUrl("ftp://files.example/a"))
    }
}
