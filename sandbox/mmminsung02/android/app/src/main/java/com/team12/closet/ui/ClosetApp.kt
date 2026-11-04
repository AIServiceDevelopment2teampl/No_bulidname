package com.team12.closet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team12.closet.R
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ClothFilter
import com.team12.closet.data.ClothUpdate
import com.team12.closet.data.ClothesPage
import com.team12.closet.data.ClothesRepository
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.UploadOutcome
import com.team12.closet.ui.closet.ClosetScreen
import com.team12.closet.ui.closet.ClosetViewModel
import com.team12.closet.ui.closet.ClothDetailScreen
import com.team12.closet.ui.register.RegisterPhotoRoute
import com.team12.closet.ui.register.RegisterPhotoViewModel
import com.team12.closet.ui.theme.AppTheme
import com.team12.closet.ui.theme.ClosetTheme
import com.team12.closet.ui.theme.InMemoryThemeStore
import com.team12.closet.ui.theme.ThemeStore
import kotlinx.coroutines.launch

// 하단 탭 4개 (docs/00-overview.md 화면 구성). 각 화면은 기능을 구현하면서 채운다
enum class MainTab(
    val label: String,
    val iconRes: Int,
) {
    HOME("홈", R.drawable.ic_tab_home),
    CLOSET("내 옷장", R.drawable.ic_tab_closet),
    SHOPPING("쇼핑", R.drawable.ic_tab_shop), // 쇼핑몰 목록으로 바로 간다 (기능 8)
    MY("마이", R.drawable.ic_tab_my),
}

@Composable
fun ClosetApp(
    clothesRepository: ClothesRepository,
    themeStore: ThemeStore,
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var chosenTheme by remember { mutableStateOf(themeStore.load()) }
    val darkTheme = chosenTheme?.let { it == AppTheme.DARK } ?: isSystemInDarkTheme()

    ClosetTheme(darkTheme = darkTheme) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selected,
                            onClick = { selected = tab },
                            icon = { Icon(painterResource(tab.iconRes), contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("tab_${tab.name}"),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("screen_${selected.name}"),
                contentAlignment = Alignment.Center,
            ) {
                when (selected) {
                    MainTab.CLOSET -> ClosetTab(clothesRepository)
                    MainTab.MY ->
                        MyTab(
                            current = if (darkTheme) AppTheme.DARK else AppTheme.LIGHT,
                            onThemeChange = {
                                themeStore.save(it)
                                chosenTheme = it
                            },
                            clothesRepository = clothesRepository,
                        )
                    else -> Text("${selected.label} 화면 (준비 중)")
                }
            }
        }
    }
}

@Composable
private fun ClosetTab(clothesRepository: ClothesRepository) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var openClothId by rememberSaveable { mutableStateOf<Long?>(null) }
    val closetViewModel = viewModel { ClosetViewModel(clothesRepository) }

    val detailCloth = openClothId?.let { closetViewModel.cloth(it) }
    when {
        registering -> {
            val registerViewModel = viewModel { RegisterPhotoViewModel(clothesRepository) }
            RegisterPhotoRoute(
                viewModel = registerViewModel,
                onDone = {
                    registerViewModel.reset()
                    registering = false
                    closetViewModel.load()
                },
            )
        }

        detailCloth != null -> {
            ClothDetailScreen(cloth = detailCloth, viewModel = closetViewModel, onClose = { openClothId = null })
        }

        else -> {
            ClosetScreen(
                viewModel = closetViewModel,
                onOpenCloth = { openClothId = it },
                onRegister = { registering = true },
            )
        }
    }
}

@Composable
private fun MyTab(
    current: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    clothesRepository: ClothesRepository,
) {
    // 마이 화면의 나머지(계정, 로그아웃, 계정 삭제)는 기능 3(account/google-login.md)에서 채운다
    var askDeleteAll by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("화면 테마")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTheme.entries.forEach { theme ->
                val label = if (theme == AppTheme.LIGHT) "라이트" else "다크"
                val modifier = Modifier.testTag("theme_${theme.name}").semantics { selected = theme == current }
                if (theme == current) {
                    Button(onClick = {}, modifier = modifier) { Text(label) }
                } else {
                    OutlinedButton(onClick = { onThemeChange(theme) }, modifier = modifier) { Text(label) }
                }
            }
        }
        OutlinedButton(
            onClick = { askDeleteAll = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 24.dp).testTag("delete_all_data"),
        ) { Text("내 데이터 전체 삭제") }
        message?.let { Text(it, modifier = Modifier.testTag("delete_all_message")) }
    }

    if (askDeleteAll) {
        AlertDialog(
            onDismissRequest = { askDeleteAll = false },
            title = { Text("내 데이터를 모두 지울까요?") },
            text = { Text("옷장, 분석한 상품, 비교 리포트, AI 점원 대화, 등록 기록이 모두 지워지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        askDeleteAll = false
                        scope.launch {
                            message =
                                when (val result = clothesRepository.deleteAllMyData()) {
                                    is ApiResult.Ok -> "모두 지웠어요"
                                    is ApiResult.Error -> result.message
                                }
                        }
                    },
                    modifier = Modifier.testTag("confirm_delete_all"),
                ) { Text("모두 삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askDeleteAll = false }) { Text("취소") } },
        )
    }
}

private object PreviewClothesRepository : ClothesRepository {
    private val error = ApiResult.Error("미리보기")

    override suspend fun uploadPhoto(photo: PickedPhoto) = UploadOutcome.Failed("미리보기")

    override suspend fun listClothes(filter: ClothFilter) = ApiResult.Ok(ClothesPage(emptyList(), 0))

    override suspend fun updateCloth(
        id: Long,
        update: ClothUpdate,
    ) = error

    override suspend fun deleteCloth(id: Long) = error

    override suspend fun deleteAllMyData() = error

    override fun imageUrl(path: String?) = null
}

@Preview(name = "라이트")
@Composable
private fun ClosetAppLightPreview() {
    ClosetApp(PreviewClothesRepository, InMemoryThemeStore(AppTheme.LIGHT))
}

@Preview(name = "다크")
@Composable
private fun ClosetAppDarkPreview() {
    ClosetApp(PreviewClothesRepository, InMemoryThemeStore(AppTheme.DARK))
}
