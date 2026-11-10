package com.team12.closet.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.team12.closet.data.AddShopResult
import com.team12.closet.data.Shop
import com.team12.closet.data.ShopRow
import com.team12.closet.data.ShopTag
import com.team12.closet.data.ShopUrls
import com.team12.closet.data.ShopsRepository
import com.team12.closet.data.toRow
import com.team12.closet.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch

private enum class ShopFilter(
    val label: String,
) {
    ALL("전체"),
    DOMESTIC("국내"),
    OVERSEAS("해외"),
    TRANSLATE("번역 필요"),
}

/** 시안 '11. 쇼핑몰 목록 (쇼핑 탭)' */
@Composable
fun ShopListScreen(
    repository: ShopsRepository,
    onOpen: (url: String) -> Unit,
) {
    var defaults by remember { mutableStateOf<List<Shop>?>(null) }
    val myShops by repository.myShops.collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf(ShopFilter.ALL) }
    var address by rememberSaveable { mutableStateOf("") }
    var addressError by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { defaults = repository.defaultShops() }

    fun openAddress() {
        val url = ShopUrls.normalize(address)
        if (shouldAsk(url) || !ShopUrls.isHttps(url)) {
            addressError = "https:// 로 시작하는 주소만 열 수 있어요"
        } else {
            addressError = null
            onOpen(url)
        }
    }

    val rows = (defaults ?: emptyList()).map { it.toRow() }

    fun visible(row: ShopRow) =
        when (filter) {
            ShopFilter.ALL -> true
            ShopFilter.DOMESTIC -> row.domestic
            ShopFilter.OVERSEAS -> !row.domestic
            ShopFilter.TRANSLATE -> row.needsTranslation
        }

    if (adding) {
        AddShopScreen(repository = repository, initialName = "", initialUrl = "", onDone = { adding = false })
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("shop_list")) {
        item {
            Text(
                "쇼핑",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    addressError = null
                },
                placeholder = { Text("쇼핑몰 주소 입력 (https://…)") },
                singleLine = true,
                isError = addressError != null,
                supportingText = addressError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { openAddress() }),
                trailingIcon = { TextButton(onClick = { openAddress() }, modifier = Modifier.testTag("address_go")) { Text("열기") } },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().testTag("address_field"),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShopFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        modifier = Modifier.testTag("shop_filter_${f.name}"),
                    )
                }
            }
        }

        val domestic = rows.filter { it.domestic && visible(it) }
        val overseas = rows.filter { !it.domestic && visible(it) }
        if (domestic.isNotEmpty()) {
            item { SectionTitle("국내 쇼핑몰") }
            items(domestic, key = { "d-" + it.url }) { ShopRowItem(it, onOpen) }
        }
        if (overseas.isNotEmpty()) {
            item { SectionTitle("해외 쇼핑몰") }
            items(overseas, key = { "o-" + it.url }) { ShopRowItem(it, onOpen) }
        }
        if (filter == ShopFilter.ALL || filter == ShopFilter.OVERSEAS) {
            item { SectionTitle("내 쇼핑몰 · 이 폰에만 저장") }
            items(myShops.map { it.toRow() }, key = { "m-" + it.url }) { row ->
                val scope = rememberCoroutineScope()
                ShopRowItem(row, onOpen, onRemove = { scope.launch { repository.removeMyShop(row.url) } })
            }
            item {
                Text(
                    "+ 쇼핑몰 직접 추가",
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { adding = true }
                            .padding(vertical = 14.dp)
                            .testTag("add_shop"),
                )
            }
        }
        item {
            Text(
                "배송 태그: 국내 국내 쇼핑몰 · 배송 가능 한국으로 바로 배송 · 배송대행 필요 배송대행지를 써야 함 · 그 외 아직 확인 안 됨. " +
                    "한국어 페이지는 번역 없이 그대로 열어요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ShopRowItem(
    row: ShopRow,
    onOpen: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(row.url) }
                .padding(vertical = 10.dp)
                .testTag("shop_${row.url}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 로고 대신 첫 글자 (Play 지식재산권 정책)
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(row.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(row.name, fontWeight = FontWeight.Bold)
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TagBadge(row.tag)
        if (onRemove != null) {
            TextButton(onClick = onRemove, modifier = Modifier.testTag("remove_${row.url}")) { Text("삭제") }
        }
    }
}

@Composable
fun TagBadge(tag: ShopTag) {
    val extra = LocalExtraColors.current
    val (bg, fg) =
        when (tag) {
            ShopTag.DOMESTIC, ShopTag.DIRECT -> extra.okBackground to extra.okText
            ShopTag.FORWARDING -> extra.warnBackground to extra.warnText
            ShopTag.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        tag.label,
        color = if (fg == Color.Unspecified) MaterialTheme.colorScheme.onSurface else fg,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 쇼핑몰 직접 추가 (목록의 '+ 쇼핑몰 직접 추가', 브라우저의 ☆).
 * 팝업 대신 화면 하나로 만든다: 글자 입력이 편하고, 팝업 안 입력칸은 UI 테스트가 끝나지 않는 문제가 있었다
 */
@Composable
fun AddShopScreen(
    repository: ShopsRepository,
    initialName: String,
    initialUrl: String,
    onDone: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var shipping by rememberSaveable { mutableStateOf("unknown") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("add_shop_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onDone, modifier = Modifier.testTag("add_shop_cancel")) { Text("〈 취소") }
        Text("쇼핑몰 직접 추가", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("이 폰에만 저장돼요. 상품 정보는 쇼핑몰마다 정해진 규칙 없이 공통 방법으로 읽어요.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, {
            name = it
        }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("add_shop_name"))
        OutlinedTextField(
            url,
            { url = it },
            label = { Text("주소 (https://…)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("add_shop_url"),
        )
        Text("한국 배송", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShopTag.choices.forEach { (code, tag) ->
                FilterChip(
                    selected = shipping == code,
                    onClick = { shipping = code },
                    label = { Text(tag.label) },
                    modifier = Modifier.testTag("add_shop_tag_$code"),
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("add_shop_error")) }
        Button(
            onClick = {
                scope.launch {
                    when (val result = repository.addMyShop(name, url, shipping)) {
                        AddShopResult.Added -> onDone()
                        is AddShopResult.Rejected -> error = result.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("add_shop_confirm"),
        ) { Text("추가") }
    }
}
