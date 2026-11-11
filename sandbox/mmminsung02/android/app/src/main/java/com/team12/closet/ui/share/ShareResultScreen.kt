package com.team12.closet.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team12.closet.data.ApiResult
import com.team12.closet.data.Cloth
import com.team12.closet.data.PhotoLoader
import com.team12.closet.data.ProductsRepository
import com.team12.closet.data.ScreenshotResult
import com.team12.closet.data.SharedItem
import com.team12.closet.ui.closet.ClothImage
import com.team12.closet.ui.compare.PurchaseReportSection
import com.team12.closet.ui.compare.ReportState
import com.team12.closet.ui.compare.SimilarClothesSection
import com.team12.closet.ui.shop.ProductCard

private sealed interface ShareState {
    data object Loading : ShareState

    data class Failed(
        val message: String,
    ) : ShareState

    data class Ready(
        val result: ScreenshotResult,
    ) : ShareState
}

const val CANNOT_OPEN_PHOTO = "사진을 열지 못했어요"

/** 시안 '19. 공유로 받은 상품': 스크린샷 → 상품 정보 카드 + 비슷한 옷 TOP 3 + 비교 리포트 */
@Composable
fun ShareResultScreen(
    shared: SharedItem.Screenshot,
    products: ProductsRepository,
    photoLoader: PhotoLoader,
    imageUrl: (String?) -> String?,
    onBack: () -> Unit,
) {
    var attempt by remember(shared) { mutableIntStateOf(0) }
    var state by remember(shared) { mutableStateOf<ShareState>(ShareState.Loading) }
    LaunchedEffect(shared, attempt) {
        state = ShareState.Loading
        val photo = photoLoader.load(shared.uri)
        state =
            if (photo == null) {
                ShareState.Failed(CANNOT_OPEN_PHOTO)
            } else {
                when (val result = products.fromScreenshot(photo)) {
                    is ApiResult.Ok -> ShareState.Ready(result.value)
                    is ApiResult.Error -> ShareState.Failed(result.message)
                }
            }
    }

    Surface(modifier = Modifier.fillMaxSize().testTag("share_result")) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("share_back")) { Text("←") }
                Text("공유로 받은 상품", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "다른 쇼핑 앱에서 공유한 스크린샷 1장",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (shared.extraCount > 0) {
                    Text("사진 ${shared.extraCount + 1}장을 받았어요. 첫 장만 읽었어요", style = MaterialTheme.typography.bodySmall)
                }
                when (val s = state) {
                    ShareState.Loading ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("스크린샷을 읽는 중…")
                        }
                    is ShareState.Failed -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        if (s.message != CANNOT_OPEN_PHOTO) Button(onClick = { attempt++ }) { Text("다시 시도") }
                    }
                    is ShareState.Ready -> ResultContent(s.result, imageUrl)
                }
                Box(Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun ResultContent(
    result: ScreenshotResult,
    imageUrl: (String?) -> String?,
) {
    val product = result.product
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (product.imagePath != null) {
            ClothImage(imageUrl(product.imagePath), modifier = Modifier.size(96.dp))
        }
        Column(modifier = Modifier.weight(1f)) { ProductCard(product) }
    }
    result.notice?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    result.similar?.let {
        HorizontalDivider()
        SimilarClothesSection(it, imageUrl = { cloth: Cloth -> imageUrl(cloth.imageUrl) })
    }
    result.report?.let {
        PurchaseReportSection(ReportState.Ready(it), startExpanded = true, modifier = Modifier.fillMaxWidth())
    }
}
