package com.team12.closet

import com.team12.closet.ui.shop.PageInjector
import com.team12.closet.ui.shop.shouldAsk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** shopping/shop-browser.md 완료 조건 [앱]: 주입 스크립트가 페이지 로드마다 한 번만 들어간다 */
class PageInjectorTest {
    private val injected = mutableListOf<String>()
    private val injector = PageInjector({ "SCRIPT" }) { injected += it }

    @Test
    fun injectsOncePerLoadEvenIfFinishedFiresTwice() {
        injector.onPageStarted()
        injector.onPageFinished("https://zozo.jp/item/1")
        injector.onPageFinished("https://zozo.jp/item/1")

        assertEquals(listOf("SCRIPT"), injected)
    }

    @Test
    fun injectsAgainOnTheNextLoad() {
        injector.onPageStarted()
        injector.onPageFinished("https://zozo.jp/item/1")
        injector.onPageStarted()
        injector.onPageFinished("https://zozo.jp/item/2")

        assertEquals(2, injected.size)
    }

    @Test
    fun neverInjectsOnLoginOrPaymentPages() {
        listOf(
            "https://www.asos.com/identity/login?returnUrl=/",
            "https://accounts.example.com/signin",
            "https://zozo.jp/_cart/checkout/",
            "https://shop.example.com/payment/confirm",
        ).forEach { url ->
            injector.onPageStarted()
            injector.onPageFinished(url)
        }

        assertEquals(emptyList<String>(), injected)
    }

    @Test
    fun onlyHttpsOpensWithoutAsking() {
        assertFalse(shouldAsk("https://zozo.jp/"))
        assertTrue(shouldAsk("http://zozo.jp/"))
        assertTrue(shouldAsk("intent://scan/#Intent;scheme=zxing;end"))
        assertTrue(shouldAsk("market://details?id=x"))
    }
}
