package com.team12.closet.ui.closet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team12.closet.data.ApiResult
import com.team12.closet.data.Cloth
import com.team12.closet.data.ClothLabels
import com.team12.closet.data.ClothUpdate
import kotlinx.coroutines.launch

@Composable
fun ClothDetailScreen(
    cloth: Cloth,
    viewModel: ClosetViewModel,
    onClose: () -> Unit,
) {
    var category by rememberSaveable(cloth.id) { mutableStateOf(cloth.category) }
    var color by rememberSaveable(cloth.id) { mutableStateOf(cloth.color) }
    var name by rememberSaveable(cloth.id) { mutableStateOf(cloth.name.orEmpty()) }
    var brand by rememberSaveable(cloth.id) { mutableStateOf(cloth.brand.orEmpty()) }
    var size by rememberSaveable(cloth.id) { mutableStateOf(cloth.size.orEmpty()) }
    var material by rememberSaveable(cloth.id) { mutableStateOf(cloth.material.orEmpty()) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(action: suspend () -> ApiResult<*>) {
        busy = true
        error = null
        scope.launch {
            when (val result = action()) {
                is ApiResult.Ok -> onClose()
                is ApiResult.Error -> error = result.message
            }
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose, modifier = Modifier.testTag("detail_back")) { Text("〈 뒤로") }
            Text("옷 상세", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!cloth.confirmed) WarnBadge("미확인", modifier = Modifier.padding(start = 8.dp))
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClothImage(viewModel.imageUrl(cloth), modifier = Modifier.fillMaxWidth().height(200.dp))
            if (!cloth.confirmed) {
                Text(
                    "자동 분류 결과예요. 맞는지 확인하고 저장해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("종류", style = MaterialTheme.typography.labelLarge)
            CategoryChips(selected = category, onSelect = { if (it != null) category = it })
            Text("색 · ${ClothLabels.color(color)}", style = MaterialTheme.typography.labelLarge)
            ColorSwatches(selected = color, onSelect = { color = it })
            DetailField("이름", name, { name = it }, "name")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailField("브랜드", brand, { brand = it }, "brand", Modifier.weight(1f))
                DetailField("사이즈", size, { size = it }, "size", Modifier.weight(0.5f))
            }
            DetailField("소재", material, { material = it }, "material", placeholder = "모르면 비워 두세요")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("detail_error")) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { askDelete = true },
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("detail_delete"),
            ) { Text("삭제") }
            Button(
                onClick = {
                    run {
                        viewModel.save(
                            cloth.id,
                            // 빈 칸은 ""로 보내 서버에서 '정보 없음'(null)이 되게 한다
                            ClothUpdate(category, color, name, brand, material, size),
                        )
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f).testTag("detail_save"),
            ) { Text("확인하고 저장") }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("이 옷을 지울까요?") },
            text = { Text("사진과 정보가 모두 지워지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        askDelete = false
                        run { viewModel.delete(cloth.id) }
                    },
                    modifier = Modifier.testTag("confirm_delete"),
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        modifier = modifier.testTag("field_$tag"),
    )
}
