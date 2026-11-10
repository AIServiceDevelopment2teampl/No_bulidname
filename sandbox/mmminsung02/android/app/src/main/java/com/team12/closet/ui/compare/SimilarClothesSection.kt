package com.team12.closet.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.team12.closet.data.Cloth
import com.team12.closet.data.SimilarCloth
import com.team12.closet.data.SimilarResult
import com.team12.closet.ui.closet.ClothImage
import com.team12.closet.ui.closet.displayName
import com.team12.closet.ui.theme.ClosetTheme

/** 화면에 보이는 단계 이름. 점수 숫자(%)는 어디에도 보여 주지 않는다 */
fun levelLabel(level: String): String =
    when (level) {
        "very_similar" -> "매우 비슷"
        "similar" -> "비슷"
        else -> "조금 다름"
    }

fun headline(result: SimilarResult): String {
    val count = result.items.count { it.level != "different" }
    return when (result.status) {
        "empty_closet" -> "비교할 옷이 아직 없어요"
        "no_similar" -> "비슷한 옷은 없어요"
        else -> "비슷한 옷 ${count}벌 있어요"
    }
}

/**
 * 시안 '7. AI 점원 패널'의 위쪽: 내 옷장에서 찾은 TOP 3.
 * 상품 화면(기능 8)·AI 점원(기능 12)에서 쓴다
 */
@Composable
fun SimilarClothesSection(
    result: SimilarResult,
    imageUrl: (Cloth) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("similar_section"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(headline(result), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text(
            if (result.status == "empty_closet") "옷을 등록하면 내 옷장에서 비슷한 옷을 찾아 드려요" else "내 옷장에서 찾았어요 (이미지 기준)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (result.items.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                result.items.forEach { item ->
                    SimilarItem(item, imageUrl(item.cloth), Modifier.weight(1f))
                }
                // 3칸을 맞춰 결과가 적어도 카드 크기가 같게
                repeat(3 - result.items.size) { Column(Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun SimilarItem(
    item: SimilarCloth,
    imageUrl: String?,
    modifier: Modifier,
) {
    val strong = item.level != "different"
    Column(modifier = modifier.testTag("similar_${item.cloth.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
        ClothImage(imageUrl, modifier = Modifier.fillMaxWidth().aspectRatio(0.85f))
        Text(
            levelLabel(item.level),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(displayName(item.cloth), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Preview(name = "비슷한 옷 TOP 3", showBackground = true, widthDp = 360)
@Composable
private fun SimilarPreview() {
    fun c(
        id: Long,
        name: String,
    ) = Cloth(id, null, "outer", "beige", name, source = "photo", confirmed = true)
    ClosetTheme(darkTheme = false) {
        Surface {
            SimilarClothesSection(
                SimilarResult(
                    "ok",
                    listOf(
                        SimilarCloth(c(1, "카멜 코트"), 0.95, "very_similar"),
                        SimilarCloth(c(2, "울 코트"), 0.84, "similar"),
                        SimilarCloth(c(3, "트렌치 코트"), 0.62, "different"),
                    ),
                ),
                imageUrl = { null },
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
