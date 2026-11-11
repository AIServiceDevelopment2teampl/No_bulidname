package com.team12.closet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team12.closet.R
import com.team12.closet.data.AddShopResult
import com.team12.closet.data.ApiResult
import com.team12.closet.data.AuthRepository
import com.team12.closet.data.ClerkRepository
import com.team12.closet.data.ClothFilter
import com.team12.closet.data.ClothUpdate
import com.team12.closet.data.ClothesPage
import com.team12.closet.data.ClothesRepository
import com.team12.closet.data.GoogleSignIn
import com.team12.closet.data.GoogleSignInResult
import com.team12.closet.data.MyShop
import com.team12.closet.data.PhotoLoader
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.ProductsRepository
import com.team12.closet.data.Session
import com.team12.closet.data.SharedItem
import com.team12.closet.data.Shop
import com.team12.closet.data.ShopsRepository
import com.team12.closet.data.TranslateRepository
import com.team12.closet.data.UploadOutcome
import com.team12.closet.ui.account.LoginScreen
import com.team12.closet.ui.account.MyScreen
import com.team12.closet.ui.closet.ClosetScreen
import com.team12.closet.ui.closet.ClosetViewModel
import com.team12.closet.ui.closet.ClothDetailScreen
import com.team12.closet.ui.register.RegisterPhotoRoute
import com.team12.closet.ui.register.RegisterPhotoViewModel
import com.team12.closet.ui.share.ShareResultScreen
import com.team12.closet.ui.shop.ShopTab
import com.team12.closet.ui.theme.AppTheme
import com.team12.closet.ui.theme.ClosetTheme
import com.team12.closet.ui.theme.InMemoryThemeStore
import com.team12.closet.ui.theme.ThemeStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

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

/** 로그인해야만 쓴다 (2026-11-04 결정). 세션이 없으면 로그인 화면, 있으면 하단 탭 화면 */
@Composable
fun ClosetApp(
    clothesRepository: ClothesRepository,
    themeStore: ThemeStore,
    authRepository: AuthRepository,
    googleSignIn: GoogleSignIn,
    shopsRepository: ShopsRepository,
    productsRepository: ProductsRepository,
    // 없으면 페이지 번역이 '번역하지 못했어요'로 끝난다 (테스트·미리보기)
    translateRepository: TranslateRepository? = null,
    // 다른 앱에서 공유로 받은 것. 로그인 전이면 로그인한 뒤에 연다
    shared: SharedItem? = null,
    onSharedHandled: () -> Unit = {},
    photoLoader: PhotoLoader = PhotoLoader { null },
    // 없으면 AI 점원 대화·어울리는 옷 없이 상품 정보만 (테스트·미리보기)
    clerkRepository: ClerkRepository? = null,
) {
    var chosenTheme by remember { mutableStateOf(themeStore.load()) }
    val darkTheme = chosenTheme?.let { it == AppTheme.DARK } ?: isSystemInDarkTheme()
    val session by authRepository.session.collectAsState()

    ClosetTheme(darkTheme = darkTheme) {
        if (session == null) {
            Surface(modifier = Modifier.fillMaxSize()) { LoginScreen(authRepository, googleSignIn) }
        } else {
            MainTabs(
                sessionKey = session!!.token,
                clothesRepository = clothesRepository,
                authRepository = authRepository,
                shopsRepository = shopsRepository,
                productsRepository = productsRepository,
                translateRepository = translateRepository,
                shared = shared,
                onSharedHandled = onSharedHandled,
                photoLoader = photoLoader,
                clerkRepository = clerkRepository,
                currentTheme = if (darkTheme) AppTheme.DARK else AppTheme.LIGHT,
                onThemeChange = {
                    themeStore.save(it)
                    chosenTheme = it
                },
            )
        }
    }
}

@Composable
private fun MainTabs(
    sessionKey: String,
    clothesRepository: ClothesRepository,
    authRepository: AuthRepository,
    shopsRepository: ShopsRepository,
    productsRepository: ProductsRepository,
    translateRepository: TranslateRepository?,
    shared: SharedItem?,
    onSharedHandled: () -> Unit,
    photoLoader: PhotoLoader,
    clerkRepository: ClerkRepository?,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
) {
    // 로그아웃하면 이 화면이 사라지므로 다시 로그인하면 홈 탭부터 시작한다
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var openLink by remember { mutableStateOf<String?>(null) }
    var screenshot by remember { mutableStateOf<SharedItem.Screenshot?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(shared) {
        when (shared) {
            null -> return@LaunchedEffect
            is SharedItem.Link -> {
                openLink = shared.url
                selected = MainTab.SHOPPING
            }
            is SharedItem.Screenshot -> screenshot = shared
            is SharedItem.Problem -> problem = shared.message
        }
        // shared가 null로 바뀌면 이 효과는 취소된다. 안내 문구는 아래에서 따로 띄운다
        onSharedHandled()
    }
    LaunchedEffect(problem) {
        problem?.let {
            snackbar.showSnackbar(it)
            problem = null
        }
    }

    screenshot?.let { item ->
        ShareResultScreen(
            shared = item,
            products = productsRepository,
            photoLoader = photoLoader,
            imageUrl = { clothesRepository.imageUrl(it) },
            onBack = {
                screenshot = null
                selected = MainTab.HOME
            },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                MainTab.CLOSET -> ClosetTab(sessionKey, clothesRepository)
                MainTab.HOME -> HomeScreen(onShop = { selected = MainTab.SHOPPING }, onCloset = { selected = MainTab.CLOSET })
                MainTab.SHOPPING ->
                    ShopTab(
                        shopsRepository,
                        productsRepository,
                        imageUrl = { clothesRepository.imageUrl(it.imageUrl) },
                        translator = translateRepository,
                        openRequest = openLink,
                        onOpenRequestHandled = { openLink = null },
                        clerk = clerkRepository,
                    )
                MainTab.MY -> MyScreen(currentTheme, onThemeChange, authRepository, clothesRepository)
            }
        }
    }
}

@Composable
private fun ClosetTab(
    sessionKey: String,
    clothesRepository: ClothesRepository,
) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var openClothId by rememberSaveable { mutableStateOf<Long?>(null) }
    // ViewModel은 화면을 떠나도 남는다. 로그인(세션)마다 새로 만들어, 다음 사람이 앞사람 옷을 보지 않게 한다
    val closetViewModel = viewModel(key = "closet-$sessionKey") { ClosetViewModel(clothesRepository) }

    val detailCloth = openClothId?.let { closetViewModel.cloth(it) }
    when {
        registering -> {
            val registerViewModel = viewModel(key = "register-$sessionKey") { RegisterPhotoViewModel(clothesRepository) }
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

// Android Studio 미리보기 전용

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

private class PreviewAuthRepository(
    loggedIn: Boolean,
) : AuthRepository {
    override val session: StateFlow<Session?> =
        MutableStateFlow(if (loggedIn) Session("preview", "홍길동", "user@example.com") else null)

    override suspend fun loginWithGoogle(
        idToken: String,
        displayName: String?,
        email: String?,
    ) = ApiResult.Error("미리보기")

    override suspend fun logout() {}

    override suspend fun deleteAccount() = ApiResult.Error("미리보기")
}

private val previewSignIn = GoogleSignIn { GoogleSignInResult.NotConfigured }

private object PreviewProductsRepository : ProductsRepository {
    override suspend fun extract(pageJson: String) = ApiResult.Error("미리보기")

    override suspend fun findSimilar(productId: Long) = ApiResult.Error("미리보기")

    override suspend fun report(productId: Long) = ApiResult.Error("미리보기")

    override suspend fun fromScreenshot(photo: PickedPhoto) = ApiResult.Error("미리보기")
}

private object PreviewShopsRepository : ShopsRepository {
    override suspend fun defaultShops() = emptyList<Shop>()

    override val myShops: Flow<List<MyShop>> = flowOf(emptyList())

    override suspend fun addMyShop(
        name: String,
        url: String,
        shipping: String,
    ) = AddShopResult.Rejected("미리보기")

    override suspend fun removeMyShop(url: String) {}
}

@Preview(name = "라이트")
@Composable
private fun ClosetAppLightPreview() {
    ClosetApp(
        PreviewClothesRepository,
        InMemoryThemeStore(AppTheme.LIGHT),
        PreviewAuthRepository(true),
        previewSignIn,
        PreviewShopsRepository,
        PreviewProductsRepository,
    )
}

@Preview(name = "다크")
@Composable
private fun ClosetAppDarkPreview() {
    ClosetApp(
        PreviewClothesRepository,
        InMemoryThemeStore(AppTheme.DARK),
        PreviewAuthRepository(true),
        previewSignIn,
        PreviewShopsRepository,
        PreviewProductsRepository,
    )
}

@Preview(name = "로그인 전")
@Composable
private fun ClosetAppLoggedOutPreview() {
    ClosetApp(
        PreviewClothesRepository,
        InMemoryThemeStore(AppTheme.LIGHT),
        PreviewAuthRepository(false),
        previewSignIn,
        PreviewShopsRepository,
        PreviewProductsRepository,
    )
}
