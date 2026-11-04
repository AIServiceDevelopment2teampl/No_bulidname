package com.team12.closet.ui.closet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.team12.closet.data.ClothLabels
import com.team12.closet.ui.theme.LocalExtraColors

/** 종류 칩. allLabel이 있으면 맨 앞에 '전체' 칩 (null 선택) */
@Composable
fun CategoryChips(
    selected: String?,
    onSelect: (String?) -> Unit,
    allLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (allLabel != null) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(allLabel) },
                modifier = Modifier.testTag("category_all"),
            )
        }
        ClothLabels.categoryOrder.forEach { code ->
            FilterChip(
                selected = selected == code,
                onClick = { onSelect(code) },
                label = { Text(ClothLabels.category(code)) },
                modifier = Modifier.testTag("category_$code"),
            )
        }
    }
}

@Composable
fun ColorSwatches(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClothLabels.swatches.forEach { (code, argb) ->
            val isSelected = selected == code
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ).clickable { onSelect(code) }
                        .testTag("color_$code")
                        .semantics {
                            contentDescription = ClothLabels.color(code)
                            this.selected = isSelected
                        },
            )
        }
    }
}

/** 주황 태그 (미확인, 사진 필요) */
@Composable
fun WarnBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalExtraColors.current
    Text(
        text = text,
        color = colors.warnText,
        style = MaterialTheme.typography.labelSmall,
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.warnBackground)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
