package com.team12.closet.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team12.closet.data.ImportKind

enum class RegisterMethod { ORDER_CAPTURE, RECEIPT, PHOTO }

/** 시안 '등록 방법 고르기' (내 옷장 → + 옷 등록) */
@Composable
fun RegisterMethodScreen(
    onChoose: (RegisterMethod) -> Unit,
    onBack: () -> Unit,
    // 서버에 사진 읽기가 없으면(테스트) 사진 등록만
    importsAvailable: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("method_back")) { Text("〈 뒤로") }
            Text("옷 등록하기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (importsAvailable) {
            Text("한 벌씩 찍는 것보다 주문내역 캡처가 훨씬 빨라요.", style = MaterialTheme.typography.bodyMedium)
            MethodCard(
                title = "주문내역 캡처",
                badge = "추천",
                body = "쇼핑 앱·사이트의 주문내역 화면을 캡처해 올리면 여러 벌을 한 번에 등록해요",
                tag = "method_order_capture",
                onClick = { onChoose(RegisterMethod.ORDER_CAPTURE) },
            )
            MethodCard(
                title = "영수증 사진",
                body = "매장에서 산 옷. 영수증엔 사진이 없어서 옷 사진은 나중에 붙여요",
                tag = "method_receipt",
                onClick = { onChoose(RegisterMethod.RECEIPT) },
            )
        }
        MethodCard(
            title = "사진으로 등록",
            body = "옷 한 벌씩 찍은 사진을 골라요. 여러 장을 한 번에 고를 수 있어요",
            tag = "method_photo",
            onClick = { onChoose(RegisterMethod.PHOTO) },
        )
        Text(
            "이 앱에서 산 옷은 결제가 끝나면 자동으로 물어봐요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MethodCard(
    title: String,
    body: String,
    tag: String,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun RegisterMethod.importKind(): ImportKind? =
    when (this) {
        RegisterMethod.ORDER_CAPTURE -> ImportKind.ORDER_CAPTURE
        RegisterMethod.RECEIPT -> ImportKind.RECEIPT
        RegisterMethod.PHOTO -> null
    }
