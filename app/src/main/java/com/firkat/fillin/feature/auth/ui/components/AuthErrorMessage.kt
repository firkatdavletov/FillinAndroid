package com.firkat.fillin.feature.auth.ui.components

import androidx.annotation.StringRes
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthError

internal val AuthError.messageRes: Int
    @StringRes get() = when (this) {
        AuthError.InvalidEmail -> R.string.email_invalid
        AuthError.WeakPassword -> R.string.email_weak_password
        AuthError.EmailInUse -> R.string.email_in_use
        AuthError.CredentialInUse -> R.string.email_credential_in_use
        AuthError.RecentLoginRequired -> R.string.email_recent_login
        AuthError.Disabled -> R.string.email_disabled
        AuthError.TooManyRequests -> R.string.email_too_many
        AuthError.EmailMismatch -> R.string.email_mismatch
        AuthError.NotConfigured -> R.string.auth_not_configured
        AuthError.NoGoogleAccount -> R.string.auth_no_google_account
        AuthError.Network -> R.string.auth_network_error
        AuthError.InvalidCredential -> R.string.auth_invalid_credential
        AuthError.SignInUnavailable -> R.string.auth_sign_in_unavailable
        AuthError.SignInFailed -> R.string.auth_sign_in_failed
        AuthError.SignOutFailed -> R.string.auth_sign_out_failed
        AuthError.CredentialResetFailed -> R.string.auth_credential_reset_failed
    }
