package com.team12.closet.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 게스트가 서버 기능을 만났을 때 보여 주는 안내 (2026-11-20 사용자 결정).
 * 게스트는 서버 계정이 없어 요청을 보내지 않고, 로그인 화면으로 가는 버튼만 둔다
 */
@Composable
fun LoginRequired(
    text: String,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp).testTag("login_required"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(
            "게스트로 둘러보는 중에는 쇼핑몰 구경만 할 수 있어요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onLogin, modifier = Modifier.testTag("login_required_button")) { Text("Google로 로그인하기") }
    }
}
