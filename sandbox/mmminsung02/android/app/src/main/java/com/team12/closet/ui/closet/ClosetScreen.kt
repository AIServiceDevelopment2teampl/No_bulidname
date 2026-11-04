package com.team12.closet.ui.closet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.team12.closet.data.Cloth
import com.team12.closet.data.ClothFilter
import com.team12.closet.data.ClothLabels
import com.team12.closet.ui.theme.ClosetTheme

@Composable
fun ClosetScreen(
    viewModel: ClosetViewModel,
    onOpenCloth: (Long) -> Unit,
    onRegister: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    ClosetContent(
        state = state,
        imageUrl = viewModel::imageUrl,
        onSelectCategory = viewModel::selectCategory,
        onSelectColor = viewModel::selectColor,
        onRetry = viewModel::load,
        onOpenCloth = onOpenCloth,
        onRegister = onRegister,
    )
}

@Composable
private fun ClosetContent(
    state: ClosetUiState,
    imageUrl: (Cloth) -> String?,
    onSelectCategory: (String?) -> Unit,
    onSelectColor: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenCloth: (Long) -> Unit,
    onRegister: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text("내 옷장", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "  ${state.total}벌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("closet_count"),
                )
            }
            CategoryChips(selected = state.filter.category, onSelect = onSelectCategory, allLabel = "전체")
            ColorSwatches(
                selected = state.filter.color,
                onSelect = onSelectColor,
                modifier = Modifier.padding(vertical = 10.dp),
            )

            val unconfirmed = state.items.count { !it.confirmed }
            val noPhoto = state.items.count { it.needsPhoto }
            if (unconfirmed + noPhoto > 0) {
                Text(
                    listOfNotNull(
                        "확인이 필요한 옷 ${unconfirmed}벌".takeIf { unconfirmed > 0 },
                        "사진이 필요한 옷 ${noPhoto}벌".takeIf { noPhoto > 0 },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            when {
                state.error != null && state.items.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("closet_retry")) { Text("다시 시도") }
                    }
                }

                state.loading && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.items.isEmpty() -> {
                    val filtered = state.filter != ClothFilter()
                    Text(
                        if (filtered) "조건에 맞는 옷이 없어요" else "아직 옷이 없어요. 주문내역 캡처로 한 번에 등록해 보세요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 48.dp).testTag("closet_empty"),
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("closet_grid"),
                    ) {
                        items(state.items, key = { it.id }) { cloth ->
                            ClothGridItem(cloth, imageUrl(cloth), onClick = { onOpenCloth(cloth.id) })
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onRegister,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("open_register_photo"),
        ) { Text("+ 옷 등록") }
    }
}

/** 이름이 없으면 '상의 · 블랙'처럼 보여 준다 */
fun displayName(cloth: Cloth): String = cloth.name ?: "${ClothLabels.category(cloth.category)} · ${ClothLabels.color(cloth.color)}"

@Composable
private fun ClothGridItem(
    cloth: Cloth,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onClick).testTag("cloth_${cloth.id}")) {
        Box {
            ClothImage(imageUrl, modifier = Modifier.fillMaxWidth().aspectRatio(0.85f))
            Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!cloth.confirmed) WarnBadge("미확인")
                if (cloth.needsPhoto) WarnBadge("사진 필요")
            }
        }
        Text(
            displayName(cloth),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun ClothImage(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val shaped = modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
    if (url == null) {
        Box(shaped, contentAlignment = Alignment.Center) {
            Text("사진 없음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = shaped)
    }
}

// Android Studio 미리보기 전용 (가짜 데이터)

private fun previewCloth(
    id: Long,
    category: String,
    color: String,
    name: String? = null,
    confirmed: Boolean = true,
    needsPhoto: Boolean = false,
) = Cloth(id, null, category, color, name, source = "photo", confirmed = confirmed, needsPhoto = needsPhoto)

@Preview(name = "내 옷장", showBackground = true, heightDp = 700)
@Composable
private fun ClosetPreview() {
    ClosetTheme(darkTheme = false) {
        Surface {
            ClosetContent(
                state =
                    ClosetUiState(
                        items =
                            listOf(
                                previewCloth(1, "outer", "beige", "카멜 코트"),
                                previewCloth(2, "top", "black", confirmed = false),
                                previewCloth(3, "top", "white", "줄무늬 티"),
                                previewCloth(4, "top", "gray", "울 니트 베스트", needsPhoto = true),
                                previewCloth(5, "bottom", "blue", "청바지"),
                            ),
                        total = 5,
                        loading = false,
                    ),
                imageUrl = { null },
                onSelectCategory = {},
                onSelectColor = {},
                onRetry = {},
                onOpenCloth = {},
                onRegister = {},
            )
        }
    }
}
