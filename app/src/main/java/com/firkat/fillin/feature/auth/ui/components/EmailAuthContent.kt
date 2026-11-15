package com.firkat.fillin.feature.auth.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.auth.ui.interactor.*
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun EmailAuthContent(
    page: EmailPage,
    email: String,
    password: String,
    confirmation: String,
    busy: Boolean,
    error: AuthError?,
    notice: String?,
    user: AuthUser?,
    resendEnabled: Boolean,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmation: (String) -> Unit,
    onPage: (EmailPage) -> Unit,
    onSubmit: (EmailTask) -> Unit,
    onGoogle: (Boolean) -> Unit,
    onLocal: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (page == EmailPage.Methods) {
        AccountMethodsContent(
            user, busy, error, notice, onGoogle = { onGoogle(false) },
            onAddPassword = {
                onPage(EmailPage.AddPassword)
            }, onVerify = { onPage(EmailPage.Verify) }, onBack = onBack, modifier = modifier
        )
        return
    }
    Column(
        modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(
            rememberScrollState()
        ).padding(24.dp).testTag("email_auth"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.email_back)) }
        Text(
            stringResource(
                when (page) {
                    EmailPage.Register -> R.string.email_register
                    EmailPage.Reset -> R.string.email_reset
                    EmailPage.Verify -> R.string.email_verify
                    EmailPage.Methods -> R.string.email_methods
                    EmailPage.AddPassword -> R.string.email_add_password
                    EmailPage.Reauthenticate -> R.string.email_recent_login
                    else -> R.string.email_login
                }
            ),
            style = MaterialTheme.typography.headlineMedium
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(
                stringResource(it.messageRes),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("email_error")
            )
        }
        notice?.let {
            Text(
                stringResource(
                    when (it) {
                        "reset" -> R.string.email_reset_sent
                        "sent" -> R.string.email_verification_sent
                        else -> R.string.email_still_unverified
                    }
                )
            )
        }
        when (page) {
            EmailPage.Verify -> {
                Text(user?.email.orEmpty())
                Button(onClick = {
                    onSubmit(EmailTask.Verify)
                }, enabled = !busy) { Text(stringResource(R.string.email_check)) }
                OutlinedButton(onClick = {
                    onSubmit(EmailTask.Resend)
                }, enabled = !busy && resendEnabled) {
                    Text(stringResource(if (resendEnabled) R.string.email_resend else R.string.email_wait))
                }
                TextButton(onClick = onLocal, enabled = !busy) { Text(stringResource(R.string.email_local)) }
                TextButton(onClick = onSignOut, enabled = !busy) { Text(stringResource(R.string.auth_sign_out)) }
            }

            else -> {
                if (page in
                    setOf(EmailPage.Login, EmailPage.Register, EmailPage.Reset)
                ) {
                    OutlinedTextField(
                        email,
                        onEmail,
                        label = {
                            Text(stringResource(R.string.email_address))
                        },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        isError =
                        error == AuthError.InvalidEmail,
                        modifier = Modifier.fillMaxWidth().testTag("email_input")
                    )
                } else {
                    Text(user?.email.orEmpty())
                }
                if (page != EmailPage.Reset &&
                    (page != EmailPage.Reauthenticate || user?.providers?.contains("password") == true)
                ) {
                    PasswordInput(
                        password,
                        onPassword,
                        !busy,
                        stringResource(R.string.email_password),
                        Modifier.testTag("password_input"),
                        isError =
                        error in setOf(AuthError.WeakPassword, AuthError.InvalidCredential)
                    )
                }
                if (page == EmailPage.Register || page == EmailPage.AddPassword) {
                    PasswordInput(
                        confirmation,
                        onConfirmation,
                        !busy,
                        stringResource(R.string.email_confirmation),
                        Modifier.testTag("confirmation_input"),
                        isError =
                        error == AuthError.WeakPassword
                    )
                    Text(stringResource(R.string.email_password_policy))
                }
                if (page != EmailPage.Reauthenticate ||
                    user?.providers?.contains("password") == true
                ) {
                    Button(
                        onClick = {
                            onSubmit(
                                when (page) {
                                    EmailPage.Register -> EmailTask.Register
                                    EmailPage.Reset -> EmailTask.Reset
                                    EmailPage.AddPassword -> EmailTask.LinkPassword
                                    EmailPage.Reauthenticate -> EmailTask.ReauthenticatePassword
                                    else -> EmailTask.Login
                                }
                            )
                        },
                        enabled =
                        !busy && (page == EmailPage.Reset || password.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth().testTag("email_submit")
                    ) {
                        Text(stringResource(R.string.email_continue))
                    }
                }
                if (page == EmailPage.Reauthenticate &&
                    user?.providers?.contains("google.com") == true
                ) {
                    OutlinedButton(onClick = {
                        onGoogle(true)
                    }, enabled = !busy) { Text(stringResource(R.string.auth_google_account)) }
                }
                if (page == EmailPage.Login) {
                    TextButton(onClick = {
                        onPage(EmailPage.Register)
                    }, enabled = !busy) { Text(stringResource(R.string.email_register)) }
                    TextButton(onClick = {
                        onPage(EmailPage.Reset)
                    }, enabled = !busy) { Text(stringResource(R.string.email_forgot)) }
                }
            }
        }
    }
}

@Composable
private fun PasswordInput(
    value: String,
    onValue: (String) -> Unit,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    initiallyVisible: Boolean = false,
    isError: Boolean = false
) {
    var visible by remember { mutableStateOf(initiallyVisible) }
    OutlinedTextField(
        value, onValue, modifier.fillMaxWidth(), enabled = enabled, singleLine = true, isError = isError,
        label = {
            Text(label)
        }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = {
                visible = !visible
            }) { Text(stringResource(if (visible) R.string.email_hide else R.string.email_show)) }
        }
    )
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PasswordHiddenPreview() {
    FillinTheme { PasswordInput("example password", {}, true, "Пароль") }
}

@Preview @Composable
private fun PasswordEmptyPreview() {
    FillinTheme { PasswordInput("", {}, true, "Пароль") }
}

@Preview @Composable
private fun PasswordDisabledPreview() {
    FillinTheme { PasswordInput("example password", {}, false, "Пароль") }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PasswordVisiblePreview() {
    FillinTheme {
        PasswordInput("example password", {}, true, stringResource(R.string.email_password), initiallyVisible = true)
    }
}

@Composable
private fun EmailPreview(
    page: EmailPage,
    busy: Boolean = false,
    error: AuthError? = null,
    notice: String? = null,
    resend: Boolean = true,
    providers: Set<String> = setOf("password")
) {
    FillinTheme {
        EmailAuthContent(
            page, "anna@example.com", "", "", busy, error, notice,
            AuthUser("preview", null, "anna@example.com", providers, page != EmailPage.Verify), resend,
            {}, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}

@Preview(name = "Login")
@Preview(name = "Login dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LoginPreview() {
    EmailPreview(EmailPage.Login)
}

@Preview(name = "Registration")
@Preview(name = "Registration dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RegistrationPreview() {
    EmailPreview(EmailPage.Register)
}

@Preview(name = "Reset")
@Preview(name = "Reset dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ResetPreview() {
    EmailPreview(EmailPage.Reset)
}

@Preview(name = "ResetSent")
@Preview(name = "ResetSent dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ResetSentPreview() {
    EmailPreview(EmailPage.Reset, notice = "reset")
}

@Preview(name = "Verification")
@Preview(name = "Verification dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun VerificationPreview() {
    EmailPreview(EmailPage.Verify)
}

@Preview(name = "VerificationSent")
@Preview(name = "VerificationSent dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun VerificationSentPreview() {
    EmailPreview(EmailPage.Verify, notice = "sent", resend = false)
}

@Preview(name = "VerificationPending")
@Preview(name = "VerificationPending dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun VerificationPendingPreview() {
    EmailPreview(EmailPage.Verify, notice = "unverified")
}

@Preview(name = "GoogleMethods")
@Preview(name = "GoogleMethods dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun GoogleMethodsPreview() {
    EmailPreview(EmailPage.Methods, providers = setOf("google.com"))
}

@Preview(name = "PasswordMethods")
@Preview(name = "PasswordMethods dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PasswordMethodsPreview() {
    EmailPreview(EmailPage.Methods)
}

@Preview(name = "BothMethods")
@Preview(name = "BothMethods dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BothMethodsPreview() {
    EmailPreview(EmailPage.Methods, providers = setOf("google.com", "password"))
}

@Preview(name = "AddPassword")
@Preview(name = "AddPassword dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AddPasswordPreview() {
    EmailPreview(EmailPage.AddPassword)
}

@Preview(name = "ReauthenticatePassword")
@Preview(name = "ReauthenticatePassword dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReauthenticatePasswordPreview() {
    EmailPreview(EmailPage.Reauthenticate)
}

@Preview(name = "ReauthenticateGoogle")
@Preview(name = "ReauthenticateGoogle dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReauthenticateGooglePreview() {
    EmailPreview(EmailPage.Reauthenticate, providers = setOf("google.com"))
}

@Preview(name = "InvalidEmail")
@Preview(name = "InvalidEmail dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun InvalidEmailPreview() {
    EmailPreview(EmailPage.Login, error = AuthError.InvalidEmail)
}

@Preview(name = "WeakPassword")
@Preview(name = "WeakPassword dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun WeakPasswordPreview() {
    EmailPreview(EmailPage.Register, error = AuthError.WeakPassword)
}

@Preview(name = "NetworkError")
@Preview(name = "NetworkError dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun NetworkErrorPreview() {
    EmailPreview(EmailPage.Verify, error = AuthError.Network)
}

@Preview(name = "Loading")
@Preview(name = "Loading dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LoadingPreview() {
    EmailPreview(EmailPage.Login, busy = true)
}

@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun EmailLargeFontPreview() {
    EmailPreview(EmailPage.Register)
}

@Preview(widthDp = 840)
@Composable
private fun EmailWidePreview() {
    EmailPreview(EmailPage.Login)
}
