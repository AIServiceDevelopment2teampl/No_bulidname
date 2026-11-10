package com.team12.closet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team12.closet.R

/** 시안 '5. 메인 화면 (베이스)': 쇼핑하기 카드 + 내 옷장 카드 */
@Composable
fun HomeScreen(
    onShop: () -> Unit,
    onCloset: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 12.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onShop)
                    .padding(20.dp)
                    .testTag("home_shop"),
        ) {
            Text("✦ 번역하고, 비교하고, 더 스마트한 쇼핑을!", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "쇼핑하기 →",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "해외 상품 번역부터\nAI 구매 비교까지, 한 번에!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .clickable(onClick = onCloset)
                    .padding(20.dp)
                    .testTag("home_closet"),
        ) {
            Text("내 옷장", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "사진으로 옷을 등록하고,\n나만의 디지털 옷장을 만들어보세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
