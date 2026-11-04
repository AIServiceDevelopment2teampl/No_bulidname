package com.team12.closet.ui.register

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.team12.closet.data.ClothLabels
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.RegisteredCloth
import com.team12.closet.data.UploadOutcome
import com.team12.closet.ui.theme.ClosetTheme

/** 실제 앱: 시스템 사진 선택기로 여러 장을 고른다 */
@Composable
fun RegisterPhotoRoute(
    viewModel: RegisterPhotoViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            viewModel.upload(uris.mapNotNull { readPhoto(context, it) })
        }
    RegisterPhotoScreen(
        viewModel = viewModel,
        onPickPhotos = {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onDone = onDone,
    )
}

private fun readPhoto(
    context: Context,
    uri: Uri,
): PickedPhoto? {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val name =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "photo.jpg"
    return PickedPhoto(name = name, mimeType = resolver.getType(uri) ?: "image/jpeg", bytes = bytes)
}

@Composable
fun RegisterPhotoScreen(
    viewModel: RegisterPhotoViewModel,
    onPickPhotos: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    RegisterPhotoContent(
        state = state,
        onPickPhotos = onPickPhotos,
        onRetry = viewModel::retry,
        onReset = viewModel::reset,
        onDone = onDone,
    )
}

@Composable
private fun RegisterPhotoContent(
    state: RegisterUiState,
    onPickPhotos: () -> Unit,
    onRetry: (PickedPhoto) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("사진으로 옷 등록", style = MaterialTheme.typography.titleLarge)
        when (val s = state) {
            RegisterUiState.Idle -> {
                Text("옷 한 벌씩 찍은 사진을 골라 주세요. 여러 장을 한 번에 고를 수 있어요.")
                Button(onClick = onPickPhotos, modifier = Modifier.testTag("pick_photos")) {
                    Text("사진 선택")
                }
            }

            is RegisterUiState.Uploading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text("분류하는 중 (${s.done + 1}/${s.total})")
                }
            }

            is RegisterUiState.Done -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).testTag("result_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.items) { item -> ResultCard(item, onRetry = { onRetry(item.photo) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReset) { Text("더 등록하기") }
                    Button(onClick = onDone, modifier = Modifier.testTag("done")) { Text("완료") }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    item: UploadItem,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("result_${item.photo.name}")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.photo.name, style = MaterialTheme.typography.labelMedium)
            when (val outcome = item.outcome) {
                is UploadOutcome.Registered -> {
                    val cloth = outcome.cloth
                    Text("${ClothLabels.category(cloth.category)} · ${ClothLabels.color(cloth.color)}")
                    if (outcome.duplicate) Text("이미 등록된 사진이에요")
                    if (cloth.needsCategory) Text("종류를 골라 주세요", color = MaterialTheme.colorScheme.error)
                    if (!cloth.confirmed) Text("미확인", style = MaterialTheme.typography.labelSmall)
                }

                is UploadOutcome.Rejected -> {
                    Text(outcome.message, color = MaterialTheme.colorScheme.error)
                }

                is UploadOutcome.Failed -> {
                    Text(outcome.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("retry_${item.photo.name}")) {
                        Text("다시 시도")
                    }
                }

                null -> {
                    Text("기다리는 중")
                }
            }
        }
    }
}

// Android Studio 미리보기 전용 (가짜 데이터)

private fun previewPhoto(name: String) = PickedPhoto(name, "image/jpeg", byteArrayOf())

private fun previewRegistered(
    category: String,
    color: String,
    needsCategory: Boolean = false,
    duplicate: Boolean = false,
) = UploadOutcome.Registered(RegisteredCloth(1, category, color, false, needsCategory), duplicate)

@Composable
private fun PreviewFrame(state: RegisterUiState) {
    ClosetTheme(darkTheme = false) {
        Surface { RegisterPhotoContent(state, {}, {}, {}, {}) }
    }
}

@Preview(name = "1. 사진 선택 전", showBackground = true, heightDp = 400)
@Composable
private fun IdlePreview() = PreviewFrame(RegisterUiState.Idle)

@Preview(name = "2. 업로드 중", showBackground = true, heightDp = 400)
@Composable
private fun UploadingPreview() = PreviewFrame(RegisterUiState.Uploading(done = 1, total = 3))

@Preview(name = "3. 결과", showBackground = true, heightDp = 800)
@Composable
private fun DonePreview() =
    PreviewFrame(
        RegisterUiState.Done(
            listOf(
                UploadItem(previewPhoto("shirt.jpg"), previewRegistered("top", "black")),
                UploadItem(previewPhoto("cap.jpg"), previewRegistered("other", "gray", needsCategory = true)),
                UploadItem(previewPhoto("jeans.jpg"), previewRegistered("bottom", "blue", duplicate = true)),
                UploadItem(previewPhoto("memo.txt"), UploadOutcome.Rejected("이미지를 읽을 수 없어요")),
                UploadItem(previewPhoto("coat.jpg"), UploadOutcome.Failed("서버에 연결하지 못했어요")),
            ),
        ),
    )
