package app.leestn.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.IOException

/** Shows Google's account picker and returns a Google ID token that the Leestn server can check. */
object GoogleSignIn {
    private fun activityOf(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /** [serverClientId] is the Web client id the Leestn server was set up with. */
    suspend fun idToken(ctx: Context, serverClientId: String): String {
        val activity = activityOf(ctx) ?: throw IOException("Couldn't open Google sign-in from here")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw IOException("Google sign-in was cancelled")
        } catch (e: NoCredentialException) {
            throw IOException("No Google account is available on this phone. Add one in the phone's settings and try again.")
        } catch (e: GetCredentialException) {
            throw IOException("Google sign-in failed: " + (e.message ?: e.type) + ". If this keeps happening, the app's Google setup may be incomplete (see the README).")
        }
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return try {
                GoogleIdTokenCredential.createFrom(cred.data).idToken
            } catch (e: Exception) {
                throw IOException("Google sign-in returned something unexpected")
            }
        }
        throw IOException("Google sign-in returned something unexpected")
    }
}
