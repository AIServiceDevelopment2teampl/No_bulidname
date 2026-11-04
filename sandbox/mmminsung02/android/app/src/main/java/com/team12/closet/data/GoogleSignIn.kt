package com.team12.closet.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed interface GoogleSignInResult {
    data class Success(
        val idToken: String,
        val displayName: String?,
        val email: String?,
    ) : GoogleSignInResult

    /** 사용자가 계정 선택 창을 닫음. 아무 말도 하지 않는다 */
    data object Cancelled : GoogleSignInResult

    /** 앱에 Google 로그인용 ID(googleWebClientId)가 아직 없음 */
    data object NotConfigured : GoogleSignInResult

    data class Failed(
        val message: String,
    ) : GoogleSignInResult
}

fun interface GoogleSignIn {
    /** activityContext: 계정 선택 창을 띄울 화면 (LocalContext) */
    suspend fun signIn(activityContext: Context): GoogleSignInResult
}

/** Android Credential Manager의 'Google로 로그인' 버튼 흐름 */
class CredentialManagerGoogleSignIn(
    private val webClientId: String,
) : GoogleSignIn {
    override suspend fun signIn(activityContext: Context): GoogleSignInResult {
        if (webClientId.isBlank()) return GoogleSignInResult.NotConfigured
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Success(google.idToken, google.displayName, google.id)
            } else {
                GoogleSignInResult.Failed("Google 계정 정보를 받지 못했어요")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Failed("이 폰에 Google 계정이 없어요. 설정에서 계정을 추가해 주세요")
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failed("Google 로그인을 열지 못했어요")
        }
    }
}
