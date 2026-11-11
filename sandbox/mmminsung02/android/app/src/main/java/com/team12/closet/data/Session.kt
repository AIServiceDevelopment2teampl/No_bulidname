package com.team12.closet.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 로그인 상태. 서버 세션 토큰과, 마이 화면에 보여 줄 이름·이메일.
 * 이름·이메일은 Google 로그인 때 받은 값을 이 폰에만 둔다 (서버는 이메일을 저장하지 않는다)
 */
data class Session(
    val token: String,
    val displayName: String? = null,
    val email: String? = null,
    /** 로그인 없이 둘러보기. 서버 계정이 없어 요청에 세션 토큰을 붙이지 않는다 */
    val guest: Boolean = false,
)

/** 게스트 세션의 자리표시 토큰. 서버로 보내지 않는다 */
const val GUEST_TOKEN = "guest"

interface SessionStore {
    fun load(): Session?

    fun save(session: Session)

    fun clear()
}

class InMemorySessionStore(
    private var session: Session? = null,
) : SessionStore {
    override fun load() = session

    override fun save(session: Session) {
        this.session = session
    }

    override fun clear() {
        session = null
    }
}

/** 세션 토큰을 Android Keystore 키(AES-GCM)로 암호화해 SharedPreferences에 둔다 */
class KeystoreSessionStore(
    context: Context,
) : SessionStore {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    override fun load(): Session? {
        val encrypted = prefs.getString(KEY_TOKEN, null) ?: return null
        val token = runCatching { decrypt(encrypted) }.getOrNull()
        if (token == null) {
            // 키가 바뀌었거나 깨진 값이면 다시 로그인하게 한다
            clear()
            return null
        }
        return Session(token, prefs.getString(KEY_NAME, null), prefs.getString(KEY_EMAIL, null), prefs.getBoolean(KEY_GUEST, false))
    }

    override fun save(session: Session) {
        prefs
            .edit()
            .putString(KEY_TOKEN, encrypt(session.token))
            .putString(KEY_NAME, session.displayName)
            .putString(KEY_EMAIL, session.email)
            .putBoolean(KEY_GUEST, session.guest)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val body = cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "session-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val KEY_TOKEN = "token"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_GUEST = "guest"
    }
}
