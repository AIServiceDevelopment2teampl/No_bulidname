package com.team12.closet.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.team12.closet.data.Cloth
import com.team12.closet.data.ProductField
import com.team12.closet.data.ProductInfo
import com.team12.closet.data.SimilarCloth
import com.team12.closet.data.SimilarResult
import com.team12.closet.ui.closet.ClothImage
import com.team12.closet.ui.closet.displayName
import com.team12.closet.ui.compare.PurchaseReportSection
import com.team12.closet.ui.compare.SimilarClothesSection
import com.team12.closet.ui.compare.headline
import com.team12.closet.ui.theme.ClosetTheme
import kotlinx.coroutines.launch

/** 상품 정보 카드에 보여 줄 칸 (translation/product-extraction.md: 상품명 / 색상 / 소재 / 핏 / 사이즈 / 세탁) */
private val CARD_FIELDS =
    listOf(
        "name" to "상품명",
        "price" to "가격",
        "color" to "색상",
        "material" to "소재",
        "fit" to "핏",
        "size" to "사이즈",
        "care" to "세탁",
    )

/** 상품 페이지가 아닐 때의 AI 점원 버튼 (일반 쇼핑 질문) */
@Composable
fun ClerkBubble(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = modifier.clickable(onClick = onClick).testTag("clerk_bubble"),
    ) {
        Text(
            "AI 점원",
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** 시안 '12. 인앱 브라우저'의 오른쪽 아래 말풍선. 누르면 패널이 열린다 */
@Composable
fun ProductBubble(
    state: ProductState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text =
        when (state) {
            ProductState.Loading -> "상품을 살펴보는 중…"
            is ProductState.Failed -> "상품 정보를 읽지 못했어요"
            is ProductState.Ready -> state.similar?.let { headline(it) } ?: "상품 정보 보기"
        }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = modifier.clickable(onClick = onClick).testTag("product_bubble"),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state == ProductState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).heightIn(max = 16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * 아래쪽 절반 패널 (시안 '7. AI 점원 패널'). 웹페이지 위에 겹쳐 그려서 열고 닫아도 페이지 스크롤이 그대로다.
 * state가 null이면 상품 페이지가 아닌 곳: 일반 질문만 받는다 (compare/ai-clerk.md)
 */
@Composable
fun ProductPanel(
    state: ProductState?,
    imageUrl: (Cloth) -> String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    clerk: ClerkSession? = null,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth().testTag("product_panel"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state == null) "AI 점원" else "이 상품",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose, modifier = Modifier.testTag("product_panel_close")) { Text("닫기") }
            }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state) {
                    null ->
                        Text(
                            "상품 페이지에서 누르면 내 옷장과 비교해 드려요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    ProductState.Loading -> CircularProgressIndicator()
                    is ProductState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is ProductState.Ready -> {
                        when {
                            state.similar != null -> SimilarClothesSection(state.similar, imageUrl)
                            state.similarError != null -> Text(state.similarError, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PurchaseReportSection(state.report)
                        clerk?.matching?.let { MatchingSection(it, imageUrl) }
                        HorizontalDivider()
                        ProductCard(state.product)
                    }
                }
                clerk?.let { ClerkConversation(it) }
            }
            clerk?.let { ClerkInput(it) }
        }
    }
}

/** 시안 7 '이 상품과 어울리는 내 옷' (옷장에 실제로 있는 옷만, 서버가 거른다) */
@Composable
private fun MatchingSection(
    state: MatchingState,
    imageUrl: (Cloth) -> String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("이 상품과 어울리는 내 옷", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        when (state) {
            MatchingState.Loading -> Text("고르는 중…", style = MaterialTheme.typography.bodySmall)
            is MatchingState.Failed -> Text(state.message, style = MaterialTheme.typography.bodySmall)
            is MatchingState.Ready -> {
                state.result.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.result.items.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("matching_${item.cloth.id}"),
                    ) {
                        ClothImage(imageUrl(item.cloth), modifier = Modifier.size(48.dp))
                        Column {
                            Text(displayName(item.cloth), style = MaterialTheme.typography.bodyMedium)
                            if (item.reason.isNotBlank()) {
                                Text(
                                    item.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClerkConversation(clerk: ClerkSession) {
    if (clerk.messages.isEmpty() && clerk.pending == null && clerk.error == null) return
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("clerk_messages")) {
        clerk.messages.forEach { Bubble(it.content, mine = it.role == "user") }
        clerk.pending?.let {
            Bubble(it, mine = true)
            Text("답을 찾는 중…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        clerk.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Bubble(
    text: String,
    mine: Boolean,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** 시안 7 아래쪽 '이 상품에 대해 물어보세요 (예: 세탁기 돼?)' */
@Composable
private fun ClerkInput(clerk: ClerkSession) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(clerk.productId) { mutableStateOf("") }
    val send = {
        scope.launch { if (clerk.send(text)) text = "" }
        Unit
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(500) },
            placeholder = {
                Text(if (clerk.productId == null) "쇼핑에 대해 물어보세요" else "이 상품에 대해 물어보세요 (예: 세탁기 돼?)")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f).testTag("clerk_input"),
        )
        TextButton(
            onClick = send,
            enabled = text.isNotBlank() && clerk.pending == null,
            modifier = Modifier.testTag("clerk_send"),
        ) { Text("보내기") }
    }
}

@Composable
fun ProductCard(product: ProductInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("product_card")) {
        CARD_FIELDS.forEach { (key, label) -> FieldRow(key, label, product.field(key)) }
    }
}

@Composable
private fun FieldRow(
    key: String,
    label: String,
    field: ProductField?,
) {
    var showOriginal by rememberSaveable(key) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().testTag("field_$key")) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (field == null) {
            // 원문에 없는 칸은 추측하지 않고 '정보 없음'
            Text("정보 없음", color = MaterialTheme.colorScheme.outline)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(field.converted ?: field.ko, modifier = Modifier.weight(1f, fill = false))
            if (field.uncertain) {
                // 시안 19: 흐린 글자는 원문과 함께 '불확실'
                Text(
                    "불확실",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("uncertain_$key"),
                )
            }
        }
        if (field.original != field.ko) {
            if (showOriginal) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(6.dp),
                ) { Text(field.original, style = MaterialTheme.typography.bodySmall) }
            }
            Text(
                if (showOriginal) "원문 숨기기" else "원문 보기",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clickable { showOriginal = !showOriginal }.padding(top = 2.dp).testTag("original_$key"),
            )
        }
    }
}

@Preview(name = "상품 패널", showBackground = true, heightDp = 800)
@Composable
private fun ProductPanelPreview() {
    val product =
        ProductInfo(
            id = 1,
            url = "https://zozo.jp/shop/beams/goods/7781/",
            lang = "ja",
            category = "top",
            hasImage = true,
            fields =
                mapOf(
                    "name" to ProductField("ウールブレンド クルーネック カーディガン", "울 블렌드 크루넥 카디건"),
                    "price" to ProductField("8800", "8800 JPY"),
                    "color" to ProductField("チャコール", "차콜"),
                    "material" to ProductField("ウール50% アクリル50%", "울 50%, 아크릴 50%"),
                    "fit" to null,
                    "size" to ProductField("Length: 25 in", "총장: 25 in", "총장: 63.5 cm (25 in)"),
                    "care" to ProductField("手洗い可", "손세탁 가능"),
                ),
        )
    val cloth = Cloth(1, null, "top", "gray", "회색 카디건", source = "photo", confirmed = true)
    ClosetTheme(darkTheme = false) {
        ProductPanel(
            ProductState.Ready(product, SimilarResult("ok", listOf(SimilarCloth(cloth, 0.93, "very_similar")))),
            imageUrl = { null },
            onClose = {},
        )
    }
}
