package com.team12.closet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.team12.closet.data.ClothesRepository
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.UploadOutcome
import com.team12.closet.ui.register.RegisterPhotoRoute
import com.team12.closet.ui.register.RegisterPhotoViewModel
import com.team12.closet.ui.theme.AppTheme
import com.team12.closet.ui.theme.ClosetTheme
import com.team12.closet.ui.theme.InMemoryThemeStore
import com.team12.closet.ui.theme.ThemeStore

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

    if (registering) {
        val registerViewModel = viewModel { RegisterPhotoViewModel(clothesRepository) }
        RegisterPhotoRoute(
            viewModel = registerViewModel,
            onDone = {
                registerViewModel.reset()
                registering = false
            },
        )
    } else {
        // 옷 목록은 기능 2(wardrobe/browse.md)에서 채운다
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("내 옷장")
            Button(onClick = { registering = true }, modifier = Modifier.testTag("open_register_photo")) {
                Text("사진으로 등록")
            }
        }
    }
}

@Composable
private fun MyTab(
    current: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
) {
    // 마이 화면의 나머지(계정, 데이터 삭제 등)는 기능 3(account/google-login.md)에서 채운다
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
    }
}

private object PreviewClothesRepository : ClothesRepository {
    override suspend fun uploadPhoto(photo: PickedPhoto) = UploadOutcome.Failed("미리보기")
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
