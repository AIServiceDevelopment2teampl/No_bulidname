package com.team12.closet.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 색 값은 화면 시안 docs/app-sitemap/index.html 의 :root(라이트), [data-theme="dark"](다크)와 같다.
// 시안 변수 이름은 주석으로 적어 둔다

val LightColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF5B5CEB), // --p
        onPrimary = Color(0xFFFFFFFF), // --on-p
        primaryContainer = Color(0xFFF1F1FE), // --p-soft
        onPrimaryContainer = Color(0xFF5B5CEB),
        secondaryContainer = Color(0xFFE4E5FD), // --p-soft2 (탭 선택 표시)
        onSecondaryContainer = Color(0xFF17182C),
        tertiary = Color(0xFFB9BAF8), // --accent-l
        background = Color(0xFFF5F5FA), // --bg
        onBackground = Color(0xFF17182C), // --text
        surface = Color(0xFFFFFFFF), // --card
        onSurface = Color(0xFF17182C),
        surfaceVariant = Color(0xFFF2F2F6), // --surface2
        onSurfaceVariant = Color(0xFF6B6D85), // --sub
        outline = Color(0xFFA4A6B8), // --muted
        outlineVariant = Color(0xFFE7E7F0), // --line
        error = Color(0xFFE5484D), // --red
    )

val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Color(0xFF8E8FFF),
        onPrimary = Color(0xFF10112A),
        primaryContainer = Color(0xFF1E1F3D),
        onPrimaryContainer = Color(0xFF8E8FFF),
        secondaryContainer = Color(0xFF2A2C5C),
        onSecondaryContainer = Color(0xFFECECF5),
        tertiary = Color(0xFF4D4FB0),
        background = Color(0xFF0C0D18),
        onBackground = Color(0xFFECECF5),
        surface = Color(0xFF161727),
        onSurface = Color(0xFFECECF5),
        surfaceVariant = Color(0xFF222338),
        onSurfaceVariant = Color(0xFFA3A5BD),
        outline = Color(0xFF6E7090),
        outlineVariant = Color(0xFF2C2E46),
        error = Color(0xFFFF6B70),
    )

/** Material 색 체계에 없는 시안 색 (태그 등) */
@Immutable
data class ExtraColors(
    val okBackground: Color, // --ok-bg (배송 가능)
    val okText: Color, // --ok-fg
    val warnBackground: Color, // --warn-bg (배송대행, 미확인)
    val warnText: Color, // --warn-fg
)

val LightExtraColors = ExtraColors(Color(0xFFDDF5E6), Color(0xFF1A7A3E), Color(0xFFFFE9C7), Color(0xFF8A5300))
val DarkExtraColors = ExtraColors(Color(0xFF123A24), Color(0xFF7EE0A4), Color(0xFF4A3410), Color(0xFFFFCB7A))

val LocalExtraColors = staticCompositionLocalOf { LightExtraColors }

@Composable
fun ClosetTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
