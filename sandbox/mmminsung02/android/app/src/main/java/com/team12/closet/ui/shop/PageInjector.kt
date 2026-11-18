package com.team12.closet.ui.shop

/**
 * 주입 스크립트를 페이지 로드마다 한 번만 넣는다 (shopping/shop-browser.md 완료 조건).
 * WebView는 한 번 로드에 onPageFinished를 여러 번 부를 수 있어서 로드 번호로 막는다.
 * 로그인·결제 페이지에는 넣지 않는다 (architecture.md 보안 규칙).
 * 예외: 결제가 끝난 '주문 완료' 주소에는 주문 상품만 읽는 스크립트(orderOnlyScript, order.js)만 넣는다
 * (auto-register/in-app.md). 번역·상품 스크립트는 넣지 않아 페이지 글자가 서버로 가지 않는다
 */
class PageInjector(
    private val script: () -> String,
    private val orderOnlyScript: (() -> String)? = null,
    private val evaluate: (String) -> Unit,
) {
    private var load = 0
    private var injectedLoad = -1

    fun onPageStarted() {
        load++
    }

    fun onPageFinished(url: String) {
        if (injectedLoad == load) return
        if (isSensitive(url)) {
            val orderScript = orderOnlyScript ?: return
            if (!isOrderDone(url)) return
            injectedLoad = load
            evaluate(orderScript())
            return
        }
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

        // 주문 완료 주소 (경로만 본다. ?returnUrl=/order/complete 같은 로그인 주소는 아니다)
        private val ORDER_DONE_PATH =
            Regex("(order|checkout|purchase|cart).*(complete|thank|confirmation|success|finish|done)", RegexOption.IGNORE_CASE)
        private val NEVER_PATH = Regex("(login|signin|sign-in|logon|/auth|password|payment|billing|/pay/)", RegexOption.IGNORE_CASE)

        fun isOrderDone(url: String): Boolean {
            val path = url.substringBefore('?').substringBefore('#')
            return ORDER_DONE_PATH.containsMatchIn(path) && !NEVER_PATH.containsMatchIn(path)
        }
    }
}
