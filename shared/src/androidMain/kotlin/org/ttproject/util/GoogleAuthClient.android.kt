package org.ttproject.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import org.ttproject.config.BuildKonfig
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class AndroidGoogleAuthClient(private val context: Context) : GoogleAuthClient {

    override suspend fun signIn(): String? {
        val credentialManager = CredentialManager.create(context)

        // 1. Set up the Google ID request
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildKonfig.WEB_CLIENT_ID) // 👈 Replace this!
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            // 2. Launch the native Android bottom sheet
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            // 3. Extract the ID Token
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                googleIdTokenCredential.idToken
            } else {
                null
            }
        } catch (e: GetCredentialException) {
            println("Google Sign In failed: ${e.message}")
            null
        } catch (e: CancellationException) {
            // User swiped away the bottom sheet
            null
        }
    }
}

@Composable
actual fun rememberGoogleAuthClient(): GoogleAuthClient {
    val context = LocalContext.current
    return remember { AndroidGoogleAuthClient(context) }
}