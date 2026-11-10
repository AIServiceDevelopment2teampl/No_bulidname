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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.team12.closet.data.Cloth
import com.team12.closet.data.ProductField
import com.team12.closet.data.ProductInfo
import com.team12.closet.data.SimilarCloth
import com.team12.closet.data.SimilarResult
import com.team12.closet.ui.compare.PurchaseReportSection
import com.team12.closet.ui.compare.SimilarClothesSection
import com.team12.closet.ui.compare.headline
import com.team12.closet.ui.theme.ClosetTheme

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

/** 아래쪽 절반 패널 (시안 '7. AI 점원 패널' 초안). 대화는 기능 12에서 붙인다 */
@Composable
fun ProductPanel(
    state: ProductState,
    imageUrl: (Cloth) -> String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth().testTag("product_panel"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이 상품", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose, modifier = Modifier.testTag("product_panel_close")) { Text("닫기") }
            }
            when (state) {
                ProductState.Loading -> CircularProgressIndicator()
                is ProductState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is ProductState.Ready -> {
                    when {
                        state.similar != null -> SimilarClothesSection(state.similar, imageUrl)
                        state.similarError != null -> Text(state.similarError, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PurchaseReportSection(state.report)
                    HorizontalDivider()
                    ProductCard(state.product)
                }
            }
        }
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
        Text(field.converted ?: field.ko)
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
