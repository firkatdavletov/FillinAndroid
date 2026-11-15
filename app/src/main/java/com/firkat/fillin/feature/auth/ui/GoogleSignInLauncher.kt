package com.firkat.fillin.feature.auth.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

fun interface GoogleSignInLauncher {
    suspend fun launch(
        context: Context
    ): String
}

class CredentialManagerGoogleSignInLauncher(private val credentialManager: CredentialManager) : GoogleSignInLauncher {
    override suspend fun launch(
        context: Context
    ): String {
        val clientIdResource = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (clientIdResource == 0) throw AuthFailure(AuthError.NotConfigured)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(clientIdResource))
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val response = credentialManager.getCredential(
            context = context,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        )
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw AuthFailure(AuthError.InvalidCredential)
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
