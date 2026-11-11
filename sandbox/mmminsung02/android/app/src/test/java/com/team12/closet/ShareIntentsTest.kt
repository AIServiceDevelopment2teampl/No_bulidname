package com.team12.closet

import android.content.Intent
import android.net.Uri
import com.team12.closet.data.ShareIntents
import com.team12.closet.data.SharedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 다른 앱이 보낸 공유 인텐트(ACTION_SEND) 해석 */
@RunWith(RobolectricTestRunner::class)
class ShareIntentsTest {
    private fun sendText(text: String) =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

    @Test
    fun textWithUrlBecomesLink() {
        assertEquals(SharedItem.Link("https://zozo.jp/x/"), ShareIntents.parse(sendText("見て https://zozo.jp/x/")))
    }

    @Test
    fun textWithoutUrlBecomesProblem() {
        assertEquals(SharedItem.Problem("링크를 찾지 못했어요"), ShareIntents.parse(sendText("예쁘다")))
    }

    @Test
    fun imageBecomesScreenshot() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://shots/1"))
            }

        assertEquals(SharedItem.Screenshot("content://shots/1", extraCount = 0), ShareIntents.parse(intent))
    }

    @Test
    fun severalImagesUseTheFirstAndCountTheRest() {
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(Uri.parse("content://shots/1"), Uri.parse("content://shots/2"), Uri.parse("content://shots/3")),
                )
            }

        assertEquals(SharedItem.Screenshot("content://shots/1", extraCount = 2), ShareIntents.parse(intent))
    }

    @Test
    fun launcherIntentIsNotAShare() {
        assertNull(ShareIntents.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(ShareIntents.parse(null))
    }
}
