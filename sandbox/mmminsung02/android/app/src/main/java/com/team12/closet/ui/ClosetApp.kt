package com.team12.closet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team12.closet.data.ClothesRepository
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.UploadOutcome
import com.team12.closet.ui.register.RegisterPhotoRoute
import com.team12.closet.ui.register.RegisterPhotoViewModel

// 하단 탭 4개 (docs/00-overview.md 화면 구성). 각 화면은 기능을 구현하면서 채운다
enum class MainTab(
    val label: String,
    val iconRes: Int,
) {
    HOME("홈", android.R.drawable.ic_menu_view),
    CLOSET("내 옷장", android.R.drawable.ic_menu_gallery),
    SHOPPING("번역", android.R.drawable.ic_menu_search),
    MY("마이", android.R.drawable.ic_menu_manage),
}

@Composable
fun ClosetApp(clothesRepository: ClothesRepository) {
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }

    MaterialTheme {
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

private object PreviewClothesRepository : ClothesRepository {
    override suspend fun uploadPhoto(photo: PickedPhoto) = UploadOutcome.Failed("미리보기")
}

@Preview
@Composable
private fun ClosetAppPreview() {
    ClosetApp(PreviewClothesRepository)
}
