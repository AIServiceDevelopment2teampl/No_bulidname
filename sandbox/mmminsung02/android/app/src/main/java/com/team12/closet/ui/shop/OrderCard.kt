package com.team12.closet.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ImportsRepository
import com.team12.closet.ui.register.CandidateList
import com.team12.closet.ui.register.ImportPicks
import kotlinx.coroutines.launch

/**
 * 시안 '결제 완료 → 옷장에 추가'. 인앱 브라우저에서 결제를 마치면 뜬다 (auto-register/in-app.md).
 * 닫으면 저장하지 않는다. 고른 것만 저장한다
 */
@Composable
fun OrderCard(
    picks: ImportPicks,
    imports: ImportsRepository?,
    onClosed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedCount by remember { mutableStateOf<Int?>(null) }

    fun close() {
        val id = picks.batch.id
        onClosed()
        if (savedCount == null) scope.launch { imports?.discard(id) }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) close() },
        modifier = Modifier.testTag("order_card"),
        title = { Text(if (savedCount == null) "옷장에 추가할까요?" else "옷장에 추가했어요", fontWeight = FontWeight.Bold) },
        text = {
            val saved = savedCount
            if (saved != null) {
                Text("${saved}벌을 내 옷장에 저장했어요.", modifier = Modifier.testTag("order_saved"))
                return@AlertDialog
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("방금 주문한 상품이에요. 고른 것만 옷장에 저장해요.", style = MaterialTheme.typography.bodyMedium)
                CandidateList(
                    picks = picks,
                    imageUrl = { imports?.imageUrl(it) },
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
                Text(
                    "이름·주소·연락처·결제 정보는 읽지 않아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (savedCount != null) {
                Button(onClick = onClosed, modifier = Modifier.testTag("order_done")) { Text("확인") }
                return@AlertDialog
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { close() }, enabled = !saving, modifier = Modifier.testTag("order_close")) { Text("닫기") }
                Button(
                    onClick = {
                        val repo = imports ?: return@Button
                        saving = true
                        error = null
                        scope.launch {
                            when (val result = repo.confirm(picks.batch.id, picks.choices())) {
                                is ApiResult.Ok -> savedCount = result.value.saved.size
                                is ApiResult.Error -> error = result.message
                            }
                            saving = false
                        }
                    },
                    enabled = picks.canSave && !saving,
                    modifier = Modifier.testTag("order_add"),
                ) { Text("옷장에 추가 (${picks.selectedCount})") }
            }
        },
        // 버튼 두 개를 confirmButton 한 줄에 둔다 (시안: 닫기 · 옷장에 추가)
        dismissButton = null,
    )
}
