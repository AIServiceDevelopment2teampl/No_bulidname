package com.team12.closet.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** 떠 있는 버블 메뉴로 가는 곳. 쇼핑몰 목록은 브라우저를 닫는 것과 같다 */
enum class DockDestination(
    val label: String,
) {
    HOME("홈"),
    CLOSET("내 옷장"),
    SHOPS("쇼핑몰 목록"),
    MY("마이"),
}

/**
 * 인앱 브라우저 위에 떠 있는 버블 하나 (2026-11-16, 웹페이지에 화면을 최대한 내주려고 하단 탭 대신).
 * 왼쪽 ☰은 다른 화면으로 가는 메뉴, 오른쪽은 상품 말풍선 또는 AI 점원 (누르면 패널).
 * product도 없고 AI 점원도 없으면 ☰만 보인다
 */
@Composable
fun BrowserDock(
    product: ProductState?,
    hasClerk: Boolean,
    destinations: List<DockDestination>,
    onOpenPanel: () -> Unit,
    onGo: (DockDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = modifier.testTag("browser_dock"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Text(
                    "☰",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier =
                        Modifier
                            .clickable { menuOpen = true }
                            .testTag("browser_menu")
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    destinations.forEach { destination ->
                        DropdownMenuItem(
                            text = { Text(destination.label) },
                            onClick = {
                                menuOpen = false
                                onGo(destination)
                            },
                            modifier = Modifier.testTag("dock_${destination.name}"),
                        )
                    }
                }
            }
            if (product != null || hasClerk) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)),
                )
                if (product != null) ProductBubble(product, onOpenPanel) else ClerkBubble(onOpenPanel)
            }
        }
    }
}

/**
 * 버블은 오른쪽 아래(여백 margin)에 붙어 있고 끌면 왼쪽·위로만 옮겨진다. 웹페이지 영역 밖으로는 못 나간다.
 * 버블 크기가 바뀌어도(말풍선 문구) 그릴 때마다 다시 맞춘다
 */
internal fun clampDock(
    offset: Offset,
    area: IntSize,
    dock: IntSize,
    margin: Float,
): Offset {
    // 영역을 아직 재지 않았으면(0) 0, 그 밖에는 음수. -0f가 나오지 않게 minOf로 쓴다
    val minX = minOf(0f, dock.width + 2 * margin - area.width)
    val minY = minOf(0f, dock.height + 2 * margin - area.height)
    return Offset(offset.x.coerceIn(minX, 0f), offset.y.coerceIn(minY, 0f))
}
