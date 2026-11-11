package com.team12.closet.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team12.closet.data.ApiResult
import com.team12.closet.data.AuthRepository
import com.team12.closet.data.ClothesRepository
import com.team12.closet.ui.theme.AppTheme
import kotlinx.coroutines.launch

private enum class Confirm { DELETE_DATA, DELETE_ACCOUNT }

/** 시안 '10. 마이'. 저장한 코디(도전 목표)는 아직 없다 */
@Composable
fun MyScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    authRepository: AuthRepository,
    clothesRepository: ClothesRepository,
) {
    val session by authRepository.session.collectAsState()
    var askLogout by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (askLogout) {
        LogoutConfirm(
            onLogout = { scope.launch { authRepository.logout() } },
            onCancel = { askLogout = false },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("마이", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                val guest = session?.guest == true
                Text(
                    if (guest) "게스트" else session?.displayName ?: "Google 계정",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("my_name"),
                )
                Text(
                    if (guest) "로그인하지 않고 둘러보는 중" else listOfNotNull(session?.email, "Google 계정").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("화면")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("테마")
                Text("처음에는 폰 설정을 따라가요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppTheme.entries.forEach { theme ->
                    val label = if (theme == AppTheme.LIGHT) "라이트" else "다크"
                    val modifier = Modifier.testTag("theme_${theme.name}").semantics { selected = theme == currentTheme }
                    if (theme == currentTheme) {
                        Button(onClick = {}, modifier = modifier) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { onThemeChange(theme) }, modifier = modifier) { Text(label) }
                    }
                }
            }
        }

        Section("내 정보")
        // TODO(기능 9 출시): 개인정보처리방침 페이지 주소가 생기면 연다
        MenuRow("개인정보처리방침 (준비 중)", tag = "my_privacy", enabled = false) {}
        if (session?.guest == true) {
            // 게스트는 지울 서버 데이터가 없다. 로그인 화면으로 돌아가 Google로 로그인한다
            MenuRow("Google로 로그인하기", tag = "my_logout") { scope.launch { authRepository.logout() } }
        } else {
            MenuRow("로그아웃", tag = "my_logout") { askLogout = true }
            MenuRow("내 데이터 전체 삭제", tag = "delete_all_data", danger = true) { confirm = Confirm.DELETE_DATA }
            MenuRow("계정 삭제", tag = "delete_account", danger = true) { confirm = Confirm.DELETE_ACCOUNT }
        }
        message?.let { Text(it, modifier = Modifier.padding(top = 12.dp).testTag("my_message")) }
    }

    when (confirm) {
        Confirm.DELETE_DATA ->
            ConfirmDialog(
                title = "내 데이터를 모두 지울까요?",
                text = "옷장, 분석한 상품, 비교 리포트, AI 점원 대화, 등록 기록이 모두 지워지고 되돌릴 수 없어요. 계정은 남아요.",
                confirmLabel = "모두 삭제",
                confirmTag = "confirm_delete_all",
                onDismiss = { confirm = null },
                onConfirm = {
                    confirm = null
                    scope.launch {
                        message =
                            when (val r = clothesRepository.deleteAllMyData()) {
                                is ApiResult.Ok -> "모두 지웠어요"
                                is ApiResult.Error -> r.message
                            }
                    }
                },
            )

        Confirm.DELETE_ACCOUNT ->
            ConfirmDialog(
                title = "계정을 삭제할까요?",
                text = "계정과 옷장, 분석한 상품, 비교 리포트, AI 점원 대화, 등록 기록이 모두 지워지고 되돌릴 수 없어요.",
                confirmLabel = "계정 삭제",
                confirmTag = "confirm_delete_account",
                onDismiss = { confirm = null },
                onConfirm = {
                    confirm = null
                    scope.launch {
                        // 성공하면 세션이 사라져 앱이 로그인 화면으로 바뀐다
                        (authRepository.deleteAccount() as? ApiResult.Error)?.let { message = it.message }
                    }
                },
            )

        null -> {}
    }
}

/** 시안 '2. 로그아웃 확인 화면' */
@Composable
private fun LogoutConfirm(
    onLogout: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("logout_confirm")) {
        TextButton(onClick = onCancel) { Text("〈 뒤로") }
        Text("로그아웃", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
        Text("정말 로그아웃하시겠습니까?", modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
        Text("지금 로그아웃하면 일부 기능을 이용할 수 없습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().testTag("confirm_logout")) { Text("로그아웃") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().testTag("cancel_logout")) { Text("취소") }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MenuRow(
    label: String,
    tag: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color =
        when {
            !enabled -> MaterialTheme.colorScheme.outline
            danger -> MaterialTheme.colorScheme.error
            else -> Color.Unspecified
        }
    Text(
        label,
        color = color,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 14.dp)
                .testTag(tag),
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    confirmTag: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(confirmTag)) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
