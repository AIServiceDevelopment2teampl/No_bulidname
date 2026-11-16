package com.team12.closet.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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

/** 화면 밖으로 밀어 숨긴 버블이 붙어 있는 가장자리 */
enum class DockEdge { LEFT, RIGHT }

/**
 * 숨긴 버블 대신 가장자리에 남는 작은 손잡이. 누르면 버블이 다시 나온다.
 * 상품 정보를 찾았으면 점을 찍어 알려 준다
 */
@Composable
fun DockHandle(
    edge: DockEdge,
    hasNews: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape =
        if (edge == DockEdge.RIGHT) {
            RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
        } else {
            RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
        }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = modifier.clickable(onClick = onClick).testTag("browser_dock_handle"),
    ) {
        Box(modifier = Modifier.size(width = 22.dp, height = 44.dp), contentAlignment = Alignment.Center) {
            Text(
                if (edge == DockEdge.RIGHT) "‹" else "›",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            if (hasNews) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 5.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                        .testTag("browser_dock_handle_dot"),
                )
            }
        }
    }
}

// 버블 위치: 오른쪽 아래(여백 margin)가 0이고 왼쪽·위로 갈수록 음수.
// 영역을 아직 재지 않았으면(0) 0, 그 밖에는 음수. -0f가 나오지 않게 minOf로 쓴다
private fun minX(
    area: IntSize,
    dock: IntSize,
    margin: Float,
) = minOf(0f, dock.width + 2 * margin - area.width)

private fun minY(
    area: IntSize,
    dock: IntSize,
    margin: Float,
) = minOf(0f, dock.height + 2 * margin - area.height)

/** 버블이 이만큼(폭의 비율) 화면 밖으로 나간 채 손을 떼면 숨긴다 */
private const val TUCK_RATIO = 0.4f

/**
 * 놓여 있는 버블은 웹페이지 영역 안에만 있다.
 * 버블 크기가 바뀌어도(말풍선 문구) 그릴 때마다 다시 맞춘다
 */
internal fun clampDock(
    offset: Offset,
    area: IntSize,
    dock: IntSize,
    margin: Float,
): Offset = Offset(offset.x.coerceIn(minX(area, dock, margin), 0f), offset.y.coerceIn(minY(area, dock, margin), 0f))

/** 끄는 동안은 양옆으로 화면 밖까지(버블 하나만큼) 밀 수 있다. 위아래는 영역 안 */
internal fun dragDock(
    offset: Offset,
    area: IntSize,
    dock: IntSize,
    margin: Float,
): Offset {
    val left = minX(area, dock, margin) - margin - dock.width
    val right = margin + dock.width
    return Offset(offset.x.coerceIn(left, right), offset.y.coerceIn(minY(area, dock, margin), 0f))
}

/** 손을 뗀 위치에서 버블이 폭의 40% 넘게 화면 밖으로 나가 있으면 그쪽 가장자리로 숨긴다 */
internal fun tuckEdge(
    offset: Offset,
    area: IntSize,
    dock: IntSize,
    margin: Float,
): DockEdge? {
    val pushed = dock.width * TUCK_RATIO
    return when {
        offset.x > margin + pushed -> DockEdge.RIGHT
        offset.x < minX(area, dock, margin) - margin - pushed -> DockEdge.LEFT
        else -> null
    }
}

/** 손잡이를 누르면 숨겼던 가장자리 안쪽, 같은 높이로 다시 나온다 */
internal fun untuckedOffset(
    edge: DockEdge,
    y: Float,
    area: IntSize,
    dock: IntSize,
    margin: Float,
): Offset = clampDock(Offset(if (edge == DockEdge.RIGHT) 0f else minX(area, dock, margin), y), area, dock, margin)
