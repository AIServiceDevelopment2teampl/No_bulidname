package com.team12.closet.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 다른 앱에서 '공유'로 받은 것 (shopping/share-intake.md) */
sealed interface SharedItem {
    /** 글에서 찾은 상품 주소. 인앱 브라우저로 연다 */
    data class Link(
        val url: String,
    ) : SharedItem

    /** 스크린샷. MVP는 첫 장만 읽고, 나머지 장 수는 화면에 알려 준다 */
    data class Screenshot(
        val uri: String,
        val extraCount: Int,
    ) : SharedItem

    data class Problem(
        val message: String,
    ) : SharedItem
}

object ShareText {
    private val URL = Regex("""https?://[^\s<>"'（）「」『』【】、。]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?)]}>…"

    /** 공유받은 글에서 첫 번째 웹 주소. http는 https로 연다 (앱은 암호화 안 된 연결을 막는다) */
    fun findUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val url = URL.find(text)?.value?.trimEnd { it in TRAILING } ?: return null
        if (url.substringAfter("://").isEmpty()) return null
        return if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
    }
}

object ShareIntents {
    const val NO_LINK = "링크를 찾지 못했어요"

    /** 공유 인텐트가 아니면 null */
    fun parse(intent: Intent?): SharedItem? {
        if (intent == null) return null
        val type = intent.type ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND ->
                when {
                    type.startsWith("image/") -> stream(intent)?.let { SharedItem.Screenshot(it.toString(), extraCount = 0) }
                    type.startsWith("text/") -> {
                        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
                        text.firstNotNullOfOrNull { ShareText.findUrl(it) }?.let { SharedItem.Link(it) } ?: SharedItem.Problem(NO_LINK)
                    }
                    else -> null
                }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (!type.startsWith("image/")) return null
                val uris = streams(intent)
                uris.firstOrNull()?.let { SharedItem.Screenshot(it.toString(), extraCount = uris.size - 1) }
            }
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun stream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
}

/** 공유받은 사진 주소(content://)에서 바이트를 읽는다. 못 읽으면 null */
fun interface PhotoLoader {
    suspend fun load(uri: String): PickedPhoto?
}

class ContentPhotoLoader(
    private val context: Context,
) : PhotoLoader {
    override suspend fun load(uri: String): PickedPhoto? =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                val resolver = context.contentResolver
                val bytes = resolver.openInputStream(parsed)?.use { it.readBytes() } ?: return@runCatching null
                val name =
                    resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "screenshot.png"
                PickedPhoto(name = name, mimeType = resolver.getType(parsed) ?: "image/png", bytes = bytes)
            }.getOrNull()
        }
}
