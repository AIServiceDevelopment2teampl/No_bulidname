package com.team12.closet.ui.register

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team12.closet.data.ImportKind

private const val MAX_CAPTURES = 5

/** 실제 앱: 시스템 사진 선택기로 캡처(최대 5장)·영수증(1장)을 고른다 */
@Composable
fun ImportRoute(
    viewModel: ImportViewModel,
    imageUrl: (String?) -> String?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val pickMany =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_CAPTURES)) { uris ->
            viewModel.read(uris.mapNotNull { readPickedPhoto(context, it) })
        }
    val pickOne =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            viewModel.read(listOfNotNull(uri?.let { readPickedPhoto(context, it) }))
        }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    ImportScreen(
        viewModel = viewModel,
        imageUrl = imageUrl,
        onPick = { if (viewModel.kind == ImportKind.RECEIPT) pickOne.launch(request) else pickMany.launch(request) },
        onDone = onDone,
    )
}

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    imageUrl: (String?) -> String?,
    onPick: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val receipt = viewModel.kind == ImportKind.RECEIPT
    BackHandler {
        if (state is ImportUiState.Review) viewModel.cancel() else onDone()
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { if (state is ImportUiState.Review) viewModel.cancel() else onDone() },
                modifier = Modifier.testTag("import_back"),
            ) { Text("〈 뒤로") }
            Text(
                if (state is ImportUiState.Review) {
                    "등록할 옷 고르기"
                } else if (receipt) {
                    "영수증 사진으로 등록"
                } else {
                    "주문내역 캡처로 등록"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        when (val s = state) {
            ImportUiState.Idle -> {
                Text(
                    if (receipt) {
                        "매장에서 받은 영수증을 찍은 사진 한 장을 골라 주세요. 영수증엔 사진이 없어서 옷 사진은 나중에 붙여요."
                    } else {
                        "쇼핑 앱·사이트의 주문내역 화면을 캡처해 골라 주세요. 한 번에 ${MAX_CAPTURES}장까지 올릴 수 있어요."
                    },
                )
                PrivacyNote()
                Button(onClick = onPick, modifier = Modifier.testTag("import_pick")) {
                    Text(if (receipt) "영수증 사진 고르기" else "캡처 고르기")
                }
            }

            is ImportUiState.Reading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(if (receipt) "영수증을 읽는 중이에요" else "캡처 ${s.photos}장을 읽는 중이에요")
                }
            }

            is ImportUiState.Review -> {
                val found = s.picks.batch.candidates.size
                Text(
                    if (receipt) {
                        "영수증에서 옷 ${found}개를 찾았어요. 틀린 건 눌러서 고쳐 주세요."
                    } else {
                        "주문내역 캡처 ${s.photos}장에서 상품 ${found}개를 찾았어요. 틀린 건 눌러서 고쳐 주세요."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("import_summary"),
                )
                CandidateList(
                    picks = s.picks,
                    imageUrl = imageUrl,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
                PrivacyNote()
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_error")) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${s.picks.selectedCount}개 선택", modifier = Modifier.weight(1f))
                    Button(
                        onClick = viewModel::save,
                        enabled = s.picks.canSave && !s.saving,
                        modifier = Modifier.testTag("import_save"),
                    ) { Text("옷장에 저장") }
                }
            }

            is ImportUiState.Saved -> {
                Text("옷장에 ${s.saved}벌을 저장했어요.", modifier = Modifier.testTag("import_saved"))
                if (s.skipped > 0) Text("이미 등록된 옷 ${s.skipped}벌은 건너뛰었어요.")
                Button(onClick = onDone, modifier = Modifier.testTag("import_done")) { Text("완료") }
            }

            is ImportUiState.Failed -> {
                Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_failed"))
                OutlinedButton(onClick = onPick) { Text("다시 고르기") }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        "주문자 이름·주소·연락처는 읽지도 저장하지도 않아요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}
