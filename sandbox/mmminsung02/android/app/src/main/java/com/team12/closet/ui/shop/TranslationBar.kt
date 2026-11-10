package com.team12.closet.ui.shop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

/** translate.js가 onTranslateState로 알리는 값 */
@Serializable
data class TranslateState(
    /** translating / done / original / korean / error */
    val status: String,
    val lang: String = "unknown",
)

@Stable
class TranslationUi {
    var state by mutableStateOf<TranslateState?>(null)

    /** 사용자가 끈 번역 (브라우저를 닫을 때까지 유지) */
    var enabled by mutableStateOf(true)

    fun clear() {
        state = null
    }
}

fun languageName(lang: String) =
    when (lang) {
        "en" -> "영어"
        "ja" -> "일본어"
        "zh" -> "중국어"
        "fr" -> "프랑스어"
        else -> "외국어"
    }

/** 시안 '12. 인앱 브라우저'의 번역 막대 */
@Composable
fun TranslationBar(
    ui: TranslationUi,
    onToggle: (Boolean) -> Unit,
) {
    val state = ui.state ?: return
    val from = languageName(state.lang)
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().testTag("translation_bar")) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val text =
                when (state.status) {
                    "korean" -> "한국어 페이지예요"
                    "translating" -> "$from → 한국어로 바꾸는 중…"
                    "error" -> "번역하지 못했어요. 원문을 보여 드려요"
                    "original" -> "원문을 보고 있어요"
                    else -> "$from → 한국어로 바꿨어요"
                }
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag("translation_text"))
            if (state.status == "done" && ui.enabled) {
                Text(
                    "원문 보기",
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(horizontal = 8.dp).clickable { onToggle(false) }.testTag("translation_show_original"),
                )
            }
            if (state.status != "korean") {
                Switch(checked = ui.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("translation_switch"))
            }
        }
    }
}
