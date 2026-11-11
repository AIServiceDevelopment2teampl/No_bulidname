package com.team12.closet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.team12.closet.data.AppDatabase
import com.team12.closet.data.ContentPhotoLoader
import com.team12.closet.data.CredentialManagerGoogleSignIn
import com.team12.closet.data.DefaultShopsRepository
import com.team12.closet.data.KeystoreSessionStore
import com.team12.closet.data.ServerConnection
import com.team12.closet.data.ShareIntents
import com.team12.closet.data.SharedItem
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.theme.SharedPrefsThemeStore

class MainActivity : ComponentActivity() {
    private val server by lazy { ServerConnection(BuildConfig.SERVER_BASE_URL, KeystoreSessionStore(applicationContext)) }
    private val themeStore by lazy { SharedPrefsThemeStore(applicationContext) }
    private val googleSignIn by lazy { CredentialManagerGoogleSignIn(BuildConfig.GOOGLE_WEB_CLIENT_ID) }
    private val shops by lazy {
        DefaultShopsRepository(
            api = server.shopsApi,
            dao = AppDatabase.create(applicationContext).myShops(),
            builtIn = DefaultShopsRepository.builtInFrom(applicationContext),
        )
    }

    // 다른 앱에서 공유로 받은 것 (shopping/share-intake.md). 화면을 돌려도 다시 처리하지 않게 처음 한 번만 읽는다
    private var shared by mutableStateOf<SharedItem?>(null)
    private val photoLoader by lazy { ContentPhotoLoader(applicationContext) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ShareIntents.parse(intent)?.let { shared = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) shared = ShareIntents.parse(intent)
        enableEdgeToEdge()
        setContent {
            // 옷 사진도 로그인 세션이 붙은 같은 OkHttpClient로 불러온다
            setSingletonImageLoaderFactory { context ->
                ImageLoader
                    .Builder(context)
                    .components { add(OkHttpNetworkFetcherFactory(callFactory = { server.httpClient })) }
                    .build()
            }
            ClosetApp(
                server.clothes,
                themeStore,
                server.auth,
                googleSignIn,
                shops,
                server.products,
                server.translate,
                shared = shared,
                onSharedHandled = { shared = null },
                photoLoader = photoLoader,
            )
        }
    }
}
