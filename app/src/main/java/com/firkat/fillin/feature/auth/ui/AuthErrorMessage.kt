package com.firkat.fillin.feature.auth.ui

import androidx.annotation.StringRes
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthError

internal val AuthError.messageRes: Int
    @StringRes get() = when (this) {
        AuthError.NotConfigured -> R.string.auth_not_configured
        AuthError.NoGoogleAccount -> R.string.auth_no_google_account
        AuthError.Network -> R.string.auth_network_error
        AuthError.InvalidCredential -> R.string.auth_invalid_credential
        AuthError.SignInUnavailable -> R.string.auth_sign_in_unavailable
        AuthError.SignInFailed -> R.string.auth_sign_in_failed
        AuthError.SignOutFailed -> R.string.auth_sign_out_failed
        AuthError.CredentialResetFailed -> R.string.auth_credential_reset_failed
    }
