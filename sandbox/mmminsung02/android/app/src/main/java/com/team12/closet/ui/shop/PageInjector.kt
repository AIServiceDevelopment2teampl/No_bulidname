package com.team12.closet.ui.shop

/**
 * 주입 스크립트를 페이지 로드마다 한 번만 넣는다 (shopping/shop-browser.md 완료 조건).
 * WebView는 한 번 로드에 onPageFinished를 여러 번 부를 수 있어서 로드 번호로 막는다.
 * 로그인·결제 페이지에는 넣지 않는다 (architecture.md 보안 규칙)
 */
class PageInjector(
    private val script: () -> String,
    private val evaluate: (String) -> Unit,
) {
    private var load = 0
    private var injectedLoad = -1

    fun onPageStarted() {
        load++
    }

    fun onPageFinished(url: String) {
        if (injectedLoad == load || isSensitive(url)) return
        injectedLoad = load
        evaluate(script())
    }

    companion object {
        // 초안 목록. 기능 6(번역)에서 실제 쇼핑몰 주소로 확인하며 늘린다
        private val SENSITIVE_URL =
            Regex(
                "(login|signin|sign-in|sign_in|logon|/auth[/?]|account/password|checkout|payment|/pay[/?]|billing|order-confirm)",
                RegexOption.IGNORE_CASE,
            )

        fun isSensitive(url: String) = SENSITIVE_URL.containsMatchIn(url)
    }
}
