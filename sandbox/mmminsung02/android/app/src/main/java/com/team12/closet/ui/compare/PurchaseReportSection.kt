package com.team12.closet.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.team12.closet.data.Cloth
import com.team12.closet.data.PurchaseReport
import com.team12.closet.data.ReportItem
import com.team12.closet.ui.theme.ClosetTheme

/** 구매 전 비교 리포트 (compare/purchase-report.md). 비슷한 옷 결과 다음에 만든다 */
sealed interface ReportState {
    /** 옷장이 비었거나 비슷한 옷을 찾지 못해 만들지 않음 */
    data object None : ReportState

    data object Loading : ReportState

    data class Ready(
        val report: PurchaseReport,
    ) : ReportState

    data class Failed(
        val message: String,
    ) : ReportState
}

const val REPORT_DISCLAIMER = "구매는 직접 판단해 주세요. 비교는 사진과 적힌 정보로만 했어요."

/** LLM 문장이 없을 때 보여 주는 비교표 한 줄 */
fun tableLine(
    item: ReportItem,
    compared: Boolean,
): String {
    val product = (item.product ?: "정보 없음") + if (item.basis == "photo") " (사진 기준)" else ""
    if (!compared) return "${item.label}: $product"
    return "${item.label}: 새 상품 $product · 내 옷 ${item.cloth ?: "정보 없음"}"
}

/**
 * 시안 '7. AI 점원 패널'의 요약 한 줄 + '리포트 전체 보기',
 * 펼치면 시안 '공유로 받은 상품'의 비슷한 점 / 다른 점 / 확인 안 된 점.
 * 문장(LLM)이 없으면 처음부터 비교표를 펼쳐 보여 준다
 */
@Composable
fun PurchaseReportSection(
    state: ReportState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ReportState.None -> {}
        ReportState.Loading ->
            Text(
                "비교 리포트를 만드는 중…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.testTag("report_loading"),
            )
        is ReportState.Failed ->
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
            )
        is ReportState.Ready -> ReportCard(state.report, modifier)
    }
}

@Composable
private fun ReportCard(
    report: PurchaseReport,
    modifier: Modifier,
) {
    val summary = report.summary
    var expanded by rememberSaveable(report.id) { mutableStateOf(summary == null) }
    val compared = report.cloth != null
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().testTag("purchase_report"),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!compared) Text(report.headline, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (expanded) "리포트 접기" else "리포트 전체 보기",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp).testTag("report_toggle"),
                )
            }
            if (expanded) {
                ReportPart("비슷한 점", report.similar, compared)
                ReportPart("다른 점", report.different, compared)
                ReportPart("확인 안 된 점", report.unknown, compared)
                Text(
                    REPORT_DISCLAIMER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ReportPart(
    title: String,
    items: List<ReportItem>,
    compared: Boolean,
) {
    if (items.isEmpty()) return
    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    items.forEach { item ->
        Row(modifier = Modifier.testTag("report_${item.field}")) {
            Text("•", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
            Text(item.text ?: tableLine(item, compared), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(name = "비교 리포트", showBackground = true)
@Composable
private fun PurchaseReportPreview() {
    val report =
        PurchaseReport(
            id = 1,
            productId = 1,
            status = "ok",
            headline = "가장 비슷한 내 옷과 비교했어요",
            cloth = Cloth(1, null, "outer", "beige", "카멜 코트", source = "photo", confirmed = true),
            level = "very_similar",
            summary = "종류와 색이 비슷하고 소재가 달라요. 핏은 내 옷 정보가 없어 비교할 수 없어요.",
            similar = listOf(ReportItem("color", "색", "카멜", "베이지", "page", "새 상품은 카멜, 내 옷은 베이지 계열이라 색이 비슷해요.")),
            different = listOf(ReportItem("material", "소재", "울 60%, 폴리에스터 40%", "면 100%")),
            unknown = listOf(ReportItem("fit", "핏", "레귤러 핏", null)),
        )
    ClosetTheme(darkTheme = false) { PurchaseReportSection(ReportState.Ready(report)) }
}
