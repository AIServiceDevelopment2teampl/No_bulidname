package com.team12.closet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.team12.closet.data.AppDatabase
import com.team12.closet.data.CredentialManagerGoogleSignIn
import com.team12.closet.data.DefaultShopsRepository
import com.team12.closet.data.KeystoreSessionStore
import com.team12.closet.data.ServerConnection
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 옷 사진도 로그인 세션이 붙은 같은 OkHttpClient로 불러온다
            setSingletonImageLoaderFactory { context ->
                ImageLoader
                    .Builder(context)
                    .components { add(OkHttpNetworkFetcherFactory(callFactory = { server.httpClient })) }
                    .build()
            }
            ClosetApp(server.clothes, themeStore, server.auth, googleSignIn, shops)
        }
    }
}
