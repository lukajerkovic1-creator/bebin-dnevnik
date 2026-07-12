package hr.bebindnevnik.app.cloud

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import hr.bebindnevnik.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AuthorizationOutcome {
    data class Authorized(
        val email: String,
        val accessToken: String,
    ) : AuthorizationOutcome

    data class NeedsResolution(
        val email: String,
        val pendingIntent: android.app.PendingIntent,
    ) : AuthorizationOutcome
}

object GoogleDriveAuthorization {
    suspend fun chooseAccount(activity: Activity): String {
        require(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Google OAuth klijent još nije konfiguriran." }
        val option =
            GetGoogleIdOption
                .Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential =
            try {
                CredentialManager.create(activity).getCredential(activity, request).credential
            } catch (error: NoCredentialException) {
                throw IllegalStateException("Na uređaju nije dostupan Google račun za prijavu.", error)
            }
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Nije odabran Google račun."
        }
        return GoogleIdTokenCredential.createFrom(credential.data).id
    }

    suspend fun authorize(
        context: Context,
        email: String,
    ): AuthorizationOutcome {
        val result = Identity.getAuthorizationClient(context).authorize(request(email)).await()
        return result.toOutcome(email)
    }

    fun complete(
        context: Context,
        email: String,
        data: Intent,
    ): AuthorizationOutcome.Authorized {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        return result.toOutcome(email) as? AuthorizationOutcome.Authorized ?: error("Google Drive dopuštenje nije dovršeno.")
    }

    suspend fun disconnect(
        context: Context,
        email: String,
    ) {
        val revoke =
            RevokeAccessRequest
                .builder()
                .setAccount(Account(email, "com.google"))
                .setScopes(listOf(Scope(GoogleDriveAppDataClient.DRIVE_SCOPE)))
                .build()
        Identity.getAuthorizationClient(context).revokeAccess(revoke).await()
    }

    private fun request(email: String): AuthorizationRequest =
        AuthorizationRequest
            .builder()
            .setAccount(Account(email, "com.google"))
            .setRequestedScopes(listOf(Scope(GoogleDriveAppDataClient.DRIVE_SCOPE)))
            .build()

    private fun AuthorizationResult.toOutcome(email: String): AuthorizationOutcome =
        if (hasResolution()) {
            AuthorizationOutcome.NeedsResolution(email, requireNotNull(pendingIntent) { "Google dopuštenje nema nastavak." })
        } else {
            AuthorizationOutcome.Authorized(email, requireNotNull(accessToken) { "Google nije vratio pristupni token." })
        }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { continuation.resume(it) }
            addOnFailureListener { continuation.resumeWithException(it) }
            addOnCanceledListener { continuation.cancel() }
        }
}
