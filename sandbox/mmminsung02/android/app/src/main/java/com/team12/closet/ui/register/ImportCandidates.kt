package com.team12.closet.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.team12.closet.data.ClothLabels
import com.team12.closet.data.ImportBatch
import com.team12.closet.data.ImportCandidate
import com.team12.closet.data.ImportChoice
import com.team12.closet.ui.closet.CategoryChips
import com.team12.closet.ui.closet.ColorSwatches
import com.team12.closet.ui.closet.WarnBadge

/** 후보마다 사용자가 고르고 고친 값 */
data class CandidateEdit(
    val selected: Boolean,
    val name: String,
    val category: String?,
    val color: String?,
)

/**
 * 확인 화면(주문내역·영수증)과 결제 완료 카드(앱에서 산 옷)가 함께 쓰는 고르기 상태.
 * 서버가 정한 기본 체크(의류 아님·이미 등록된 옷은 해제)에서 시작한다
 */
@Stable
class ImportPicks(
    val batch: ImportBatch,
) {
    private val edits =
        mutableStateMapOf<Int, CandidateEdit>().apply {
            batch.candidates.forEach { put(it.index, CandidateEdit(it.selected, it.name, it.category, it.color)) }
        }

    fun edit(index: Int): CandidateEdit = edits.getValue(index)

    fun toggle(index: Int) {
        edits[index] = edit(index).let { it.copy(selected = !it.selected) }
    }

    fun update(
        index: Int,
        change: CandidateEdit,
    ) {
        edits[index] = change
    }

    val selectedCount: Int get() = edits.values.count { it.selected }

    /** 사진이 없는 옷은 색을 서버가 정할 수 없어 사용자가 골라야 한다 */
    fun needsColor(candidate: ImportCandidate) = edit(candidate.index).let { it.selected && it.color == null && !candidate.hasImage }

    val canSave: Boolean get() = selectedCount > 0 && batch.candidates.none { needsColor(it) }

    fun choices(): List<ImportChoice> =
        batch.candidates.mapNotNull { candidate ->
            val e = edit(candidate.index)
            if (!e.selected) return@mapNotNull null
            ImportChoice(
                index = candidate.index,
                category = e.category,
                color = e.color,
                name = e.name.trim().takeIf { it.isNotEmpty() && it != candidate.name },
            )
        }
}

@Composable
fun CandidateList(
    picks: ImportPicks,
    imageUrl: (String?) -> String?,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<ImportCandidate?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        picks.batch.candidates.forEach { candidate ->
            CandidateRow(
                candidate = candidate,
                edit = picks.edit(candidate.index),
                needsColor = picks.needsColor(candidate),
                imageUrl = imageUrl(candidate.imageUrl),
                onToggle = { picks.toggle(candidate.index) },
                onOpen = { editing = candidate },
            )
        }
    }
    editing?.let { candidate ->
        EditCandidateDialog(
            start = picks.edit(candidate.index),
            onDismiss = { editing = null },
            onSave = {
                picks.update(candidate.index, it.copy(selected = true))
                editing = null
            },
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: ImportCandidate,
    edit: CandidateEdit,
    needsColor: Boolean,
    imageUrl: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable(onClick = onOpen)
                .padding(8.dp)
                .testTag("candidate_${candidate.index}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = edit.selected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("candidate_check_${candidate.index}"),
        )
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
            } else {
                Text("👕", style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    edit.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!candidate.hasImage && candidate.isClothing && !candidate.duplicate) WarnBadge("사진 필요")
            }
            if (candidate.duplicate) {
                Text("이미 등록된 옷이에요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    describe(candidate, edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (needsColor) {
                Text("색을 골라 주세요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            if (candidate.isClothing) ClothLabels.category(edit.category ?: "other") else "의류 아님",
            style = MaterialTheme.typography.labelMedium,
            color = if (candidate.isClothing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

/** 시안: '화이트 · L · ₩39,900 · 10/12'. 정해진 색이 없으면 쇼핑몰 옵션 글자 그대로 */
private fun describe(
    candidate: ImportCandidate,
    edit: CandidateEdit,
): String {
    val date =
        candidate.date
            ?.split("-")
            ?.takeIf { it.size == 3 }
            ?.let { "${it[1].toInt()}/${it[2].toInt()}" }
    val color = edit.color?.let { ClothLabels.color(it) } ?: candidate.option
    return listOfNotNull(color, candidate.size, candidate.price, date).joinToString(" · ")
}

@Composable
private fun EditCandidateDialog(
    start: CandidateEdit,
    onDismiss: () -> Unit,
    onSave: (CandidateEdit) -> Unit,
) {
    var name by remember { mutableStateOf(start.name) }
    var category by remember { mutableStateOf(start.category) }
    var color by remember { mutableStateOf(start.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("고치기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("candidate_name"),
                )
                Text("종류", style = MaterialTheme.typography.labelLarge)
                CategoryChips(selected = category, onSelect = { if (it != null) category = it })
                Text("색" + (color?.let { " · ${ClothLabels.color(it)}" } ?: ""), style = MaterialTheme.typography.labelLarge)
                ColorSwatches(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(start.copy(name = name, category = category, color = color)) },
                modifier = Modifier.testTag("candidate_save"),
            ) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
