package com.team12.closet.ui.shop

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ClerkRepository
import com.team12.closet.data.Cloth
import com.team12.closet.data.ShopUrls
import com.team12.closet.data.TranslateRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject

/** 브라우저 화면 상태. 테스트에서 직접 만들어 넘길 수 있다 */
@Stable
class BrowserState(
    startUrl: String,
) {
    var currentUrl by mutableStateOf(startUrl)
    var title by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var loadFailed by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    /** 그대로 열지 않고 사용자에게 물을 주소 (http://, 앱 실행 링크) */
    var askToOpen by mutableStateOf<String?>(null)

    fun onMainFrameError() {
        loading = false
        loadFailed = true
    }
}

/** https는 그대로 연다. http:// 와 앱 실행 링크(intent:// 등)는 묻는다 */
fun shouldAsk(url: String): Boolean = !url.startsWith("https://", ignoreCase = true)

/**
 * 주입 스크립트(product.js, translate.js)가 부르는 다리. WebView의 다른 스레드에서 불리므로 메인 스레드로 넘긴다.
 * 페이지에 내보내는 함수는 글자만 받는 아래 세 개뿐이다
 */
class PageBridge(
    private val onProduct: (String) -> Unit,
    private val onTranslate: (requestId: String, textsJson: String) -> Unit,
    private val onTranslateState: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onProduct(pageJson: String) {
        main.post { onProduct.invoke(pageJson) }
    }

    @JavascriptInterface
    fun translate(
        requestId: String,
        textsJson: String,
    ) {
        main.post { onTranslate.invoke(requestId, textsJson) }
    }

    @JavascriptInterface
    fun onTranslateState(stateJson: String) {
        main.post { onTranslateState.invoke(stateJson) }
    }
}

private val bridgeJson = Json { ignoreUnknownKeys = true }

const val MAX_TRANSLATE_REQUESTS_PER_PAGE = 30

/** 앱 → 페이지 호출. 글자는 JSON 문자열로 감싸 그대로 넘긴다 */
private fun jsCall(
    function: String,
    vararg args: String,
) = "window.__dupecheck && window.__dupecheck.translate && window.__dupecheck.translate.$function(" +
    args.joinToString(",") { JSONObject.quote(it) } + ");"

/** 시안 '12. 인앱 브라우저'. 번역 막대는 기능 6, AI 점원 대화는 기능 12에서 붙인다 */
@Composable
fun BrowserScreen(
    state: BrowserState,
    onClose: () -> Unit,
    onAddToMyShops: (name: String, url: String) -> Unit,
    script: () -> String,
    lookup: ProductLookup? = null,
    imageUrl: (Cloth) -> String? = { null },
    translator: TranslateRepository? = null,
    translation: TranslationUi = remember { TranslationUi() },
    // AI 점원 (compare/ai-clerk.md). 없으면 대화·어울리는 옷 없이 상품 정보만
    clerk: ClerkRepository? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    val onProduct: (String) -> Unit = { json -> scope.launch { lookup?.onProductPage(json) } }
    // 페이지 하나가 보낼 수 있는 번역 요청 수. 악성 페이지가 끝없이 요청해 비용을 쓰지 못하게 (서버에도 상한)
    val translateRequests = remember { intArrayOf(0) }
    val onNewPage: () -> Unit = {
        lookup?.clear()
        translation.clear()
        translateRequests[0] = 0
        panelOpen = false
    }
    val bridge =
        remember {
            PageBridge(
                onProduct = { json -> onProduct(json) },
                onTranslate = { id, textsJson ->
                    if (++translateRequests[0] > MAX_TRANSLATE_REQUESTS_PER_PAGE) {
                        webView?.evaluateJavascript(jsCall("fail", id), null)
                        return@PageBridge
                    }
                    scope.launch {
                        val texts = runCatching { bridgeJson.decodeFromString<List<String>>(textsJson) }.getOrNull()
                        val result = texts?.let { translator?.translate(it) }
                        val js =
                            if (result is ApiResult.Ok) {
                                jsCall("apply", id, bridgeJson.encodeToString(result.value))
                            } else {
                                jsCall("fail", id)
                            }
                        webView?.evaluateJavascript(js, null)
                    }
                },
                onTranslateState = { json ->
                    runCatching { bridgeJson.decodeFromString<TranslateState>(json) }.getOrNull()?.let { translation.state = it }
                },
            )
        }
    // 사용자가 번역을 껐으면 새 페이지에서도 시작하지 않는다
    val pageScript = {
        (if (translation.enabled) "" else "window.__dupecheckTranslateOff=true;\n") + script() +
            "\n;if(!window.__dupecheckTranslateOff&&window.__dupecheck&&window.__dupecheck.translate)window.__dupecheck.translate.start();"
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Column(modifier = Modifier.fillMaxSize().testTag("browser")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose, modifier = Modifier.testTag("browser_close")) { Text("✕") }
            Text(
                (if (state.currentUrl.startsWith("https://")) "🔒 " else "") + ShopUrls.host(state.currentUrl),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("browser_url"),
            )
        }
        TranslationBar(translation) { on ->
            translation.enabled = on
            webView?.evaluateJavascript(jsCall(if (on) "showTranslation" else "showOriginal"), null)
        }
        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx -> createWebView(ctx, state, pageScript, bridge, onNewPage).also { webView = it } },
                modifier = Modifier.fillMaxSize().testTag("browser_web"),
            )
            if (state.loadFailed) {
                LoadError(
                    onRetry = {
                        state.loadFailed = false
                        webView?.reload()
                    },
                    onExternal = { openExternally(context, state.currentUrl) },
                )
            }
            val product = lookup?.state
            val productId = ((product as? ProductState.Ready)?.product)?.id
            // 상품마다 대화가 따로다. 상품 페이지가 아니면 일반 대화(productId = null)
            val clerkSession = remember(productId, clerk) { clerk?.let { ClerkSession(it, productId) } }
            LaunchedEffect(panelOpen, clerkSession) { if (panelOpen) clerkSession?.open() }
            if ((product != null || clerkSession != null) && !state.loadFailed) {
                if (panelOpen) {
                    ProductPanel(
                        state = product,
                        imageUrl = imageUrl,
                        onClose = { panelOpen = false },
                        clerk = clerkSession,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.6f),
                    )
                } else if (product != null) {
                    ProductBubble(
                        state = product,
                        onClick = { panelOpen = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    )
                } else {
                    ClerkBubble(onClick = { panelOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
                }
            }
        }

        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(
                onClick = { webView?.goBack() },
                enabled = state.canGoBack,
                modifier = Modifier.testTag("browser_back"),
            ) { Text("〈") }
            TextButton(
                onClick = { webView?.goForward() },
                enabled = state.canGoForward,
                modifier = Modifier.testTag("browser_forward"),
            ) { Text("〉") }
            TextButton(onClick = { webView?.reload() }, modifier = Modifier.testTag("browser_reload")) { Text("↻") }
            TextButton(
                onClick = { onAddToMyShops(state.title ?: ShopUrls.host(state.currentUrl), state.currentUrl) },
                modifier = Modifier.testTag("browser_add_shop"),
            ) { Text("☆") }
            TextButton(
                onClick = { openExternally(context, state.currentUrl) },
                modifier = Modifier.testTag("browser_external"),
            ) { Text("↗") }
        }
    }

    state.askToOpen?.let { url ->
        val insecure = url.startsWith("http://", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { state.askToOpen = null },
            title = { Text(if (insecure) "보안 연결이 아닌 주소예요" else "다른 앱으로 열까요?") },
            text = { Text(if (insecure) "주고받는 내용이 암호화되지 않아요. 그래도 열까요?\n$url" else url) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.askToOpen = null
                        if (insecure) webView?.loadUrl(url) else openExternally(context, url)
                    },
                    modifier = Modifier.testTag("ask_open"),
                ) { Text("열기") }
            },
            dismissButton = { TextButton(onClick = { state.askToOpen = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun LoadError(
    onRetry: () -> Unit,
    onExternal: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize().testTag("browser_error")) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("페이지를 열지 못했어요", style = MaterialTheme.typography.titleMedium)
            Text(
                "인터넷 연결을 확인하거나, 이 사이트가 앱 안에서 열리지 않으면 외부 브라우저로 열어 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry, modifier = Modifier.testTag("browser_retry")) { Text("다시 시도") }
            OutlinedButton(onClick = onExternal) { Text("외부 브라우저로 열기") }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    state: BrowserState,
    script: () -> String,
    bridge: PageBridge,
    onNewPage: () -> Unit,
): WebView {
    val view = WebView(context)
    view.settings.javaScriptEnabled = true
    view.settings.domStorageEnabled = true
    view.addJavascriptInterface(bridge, "DupeCheckBridge")
    val injector = PageInjector(script) { js -> view.evaluateJavascript(js, null) }
    view.webViewClient =
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url.toString()
                if (!shouldAsk(url)) return false
                state.askToOpen = url
                return true
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: Bitmap?,
            ) {
                injector.onPageStarted()
                onNewPage()
                state.currentUrl = url
                state.loading = true
                state.loadFailed = false
            }

            override fun onPageFinished(
                view: WebView,
                url: String,
            ) {
                injector.onPageFinished(url)
                state.loading = false
                state.title = view.title?.takeIf { it.isNotBlank() }
                state.canGoBack = view.canGoBack()
                state.canGoForward = view.canGoForward()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                // 페이지 안 이미지·광고 하나가 실패한 것은 무시한다
                if (request.isForMainFrame) state.onMainFrameError()
            }
        }
    view.loadUrl(state.currentUrl)
    return view
}

fun openExternally(
    context: Context,
    url: String,
) {
    val intent =
        if (url.startsWith("intent:", ignoreCase = true)) {
            runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
