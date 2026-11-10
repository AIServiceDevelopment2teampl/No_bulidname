package com.team12.closet.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team12.closet.R
import com.team12.closet.data.ApiResult
import com.team12.closet.data.AuthRepository
import com.team12.closet.data.GoogleSignIn
import com.team12.closet.data.GoogleSignInResult
import com.team12.closet.ui.theme.ClosetTheme
import kotlinx.coroutines.launch

/** 시안 docs/app-sitemap/index.html '1. 로그인 화면' */
@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    googleSignIn: GoogleSignIn,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    LoginContent(
        busy = busy,
        message = message,
        onGoogle = {
            busy = true
            message = null
            scope.launch {
                message =
                    when (val result = googleSignIn.signIn(context)) {
                        is GoogleSignInResult.Success -> {
                            when (val login = authRepository.loginWithGoogle(result.idToken, result.displayName, result.email)) {
                                is ApiResult.Ok -> null // 세션이 생기면 앱이 홈으로 바꾼다
                                is ApiResult.Error -> login.message
                            }
                        }

                        GoogleSignInResult.Cancelled -> null
                        GoogleSignInResult.NotConfigured -> "앱에 Google 로그인 설정이 아직 없어요"
                        is GoogleSignInResult.Failed -> result.message
                    }
                busy = false
            }
        },
    )
}

@Composable
private fun LoginContent(
    busy: Boolean,
    message: String?,
    onGoogle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("login_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(120.dp))
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_tab_closet),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 14.dp))
        Text(
            "해외 쇼핑도, 내 옷장도\nAI와 함께",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp).testTag("login_message"),
            )
        }
        OutlinedButton(
            onClick = onGoogle,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("login_google"),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Google로 계속하기", fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            modifier = Modifier.padding(top = 12.dp, bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "처음이면 Google 계정으로 바로 가입돼요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // TODO(기능 9 출시): 이용약관·개인정보처리방침 페이지 주소가 생기면 링크로 바꾼다
            Text(
                "계속하면 이용약관과 개인정보처리방침에 동의하게 돼요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "로그인", showBackground = true, heightDp = 700)
@Composable
private fun LoginPreview() {
    ClosetTheme(darkTheme = false) { Surface { LoginContent(busy = false, message = null, onGoogle = {}) } }
}
