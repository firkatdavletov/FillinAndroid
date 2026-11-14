package com.firkat.fillin.feature.auth.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.ui.interactor.AuthButton
import com.firkat.fillin.ui.component.PrimaryButton
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun AuthButtons(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    loadingButton: AuthButton = AuthButton.Google,
    onGoogleClick: () -> Unit,
    onEmailClick: () -> Unit,
    onGuestClick: () -> Unit,
    googleEnabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PrimaryButton(
            text = stringResource(R.string.auth_continue_google),
            onClick = onGoogleClick,
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading && (loadingButton == AuthButton.Google),
            enabled = !isLoading && googleEnabled,
            backgroundColor = colors.surface,
            textColor = colors.onSurface,
            leadingIcon = painterResource(R.drawable.ic_google),
            iconTint = Color.Unspecified,
            border = BorderStroke(
                1.dp,
                colors.outlineVariant
            )
        )

        PrimaryButton(
            text = stringResource(R.string.auth_continue_email),
            onClick = onEmailClick,
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading && (loadingButton == AuthButton.Email),
            enabled = !isLoading,
            leadingIcon = painterResource(R.drawable.ic_mail_24)
        )

        PrimaryButton(
            text = stringResource(R.string.auth_continue_guest),
            onClick = onGuestClick,
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading && (loadingButton == AuthButton.Guest),
            enabled = !isLoading,
            backgroundColor = colors.surfaceVariant,
            textColor = colors.onSurfaceVariant,
            leadingIcon = painterResource(R.drawable.ic_guest_24)
        )
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AuthButtons_Preview() {
    FillinTheme {
        AuthButtons(
            onGoogleClick = {},
            onEmailClick = {},
            onGuestClick = {}
        )
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AuthButtonsGoogleLoadingPreview() {
    FillinTheme {
        AuthButtons(isLoading = true, onGoogleClick = {}, onEmailClick = {}, onGuestClick = {})
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AuthButtonsEmailLoadingPreview() {
    FillinTheme {
        AuthButtons(isLoading = true, loadingButton = AuthButton.Email, onGoogleClick = {
        }, onEmailClick = {}, onGuestClick = {})
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AuthButtonsGuestLoadingPreview() {
    FillinTheme {
        AuthButtons(isLoading = true, loadingButton = AuthButton.Guest, onGoogleClick = {
        }, onEmailClick = {}, onGuestClick = {})
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AuthButtonsGoogleUnavailablePreview() {
    FillinTheme {
        AuthButtons(googleEnabled = false, onGoogleClick = {}, onEmailClick = {}, onGuestClick = {})
    }
}
