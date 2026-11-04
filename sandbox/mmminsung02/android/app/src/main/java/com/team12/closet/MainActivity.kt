package com.team12.closet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.team12.closet.data.NetworkClothesRepository
import com.team12.closet.ui.ClosetApp
import com.team12.closet.ui.theme.SharedPrefsThemeStore

class MainActivity : ComponentActivity() {
    private val clothesRepository by lazy { NetworkClothesRepository.create(BuildConfig.SERVER_BASE_URL) }
    private val themeStore by lazy { SharedPrefsThemeStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ClosetApp(clothesRepository, themeStore) }
    }
}
