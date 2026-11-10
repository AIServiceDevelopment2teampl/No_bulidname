package com.team12.closet.ui.shop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.team12.closet.data.Cloth
import com.team12.closet.data.ProductsRepository
import com.team12.closet.data.ShopUrls
import com.team12.closet.data.ShopsRepository

/** 쇼핑 탭: 쇼핑몰 목록 ↔ 인앱 브라우저 */
@Composable
fun ShopTab(
    repository: ShopsRepository,
    products: ProductsRepository,
    imageUrl: (Cloth) -> String?,
) {
    val context = LocalContext.current
    var openUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var addFromBrowser by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 진입점 + 상품 감지 스크립트를 한 번에 넣는다
    val script =
        remember {
            {
                listOf("bootstrap.js", "product.js").joinToString("\n") { name ->
                    context.assets.open(name).use { it.readBytes().decodeToString() }
                }
            }
        }
    // 추가 화면에 다녀와도 보던 페이지로 돌아오도록 브라우저 상태는 여기서 들고 있는다
    val browserState = remember(openUrl) { openUrl?.let { BrowserState(it) } }
    val lookup = remember(openUrl) { ProductLookup(products) }

    addFromBrowser?.let { (name, siteUrl) ->
        AddShopScreen(repository = repository, initialName = name, initialUrl = siteUrl, onDone = { addFromBrowser = null })
        return
    }

    if (browserState == null) {
        ShopListScreen(repository = repository, onOpen = { openUrl = it })
    } else {
        BrowserScreen(
            state = browserState,
            onClose = { openUrl = null },
            // 보고 있는 페이지의 사이트 첫 화면을 내 쇼핑몰로 넣는다
            onAddToMyShops = { name, pageUrl -> addFromBrowser = name to "https://${ShopUrls.host(pageUrl)}/" },
            script = script,
            lookup = lookup,
            imageUrl = imageUrl,
        )
    }
}
