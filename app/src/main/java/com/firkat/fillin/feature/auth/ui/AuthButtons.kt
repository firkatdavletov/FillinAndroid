package com.firkat.fillin.feature.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.component.PrimaryButton
import com.firkat.fillin.ui.theme.FillinTheme

private enum class ClickedButton {
    GOOGLE, EMAIL, GUEST,
}

@Composable
fun AuthButtons(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onGoogleClick: () -> Unit,
    onEmailClick: () -> Unit,
    onGuestClick: () -> Unit,
    googleEnabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    var clickedButton by rememberSaveable { mutableStateOf(ClickedButton.GOOGLE) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrimaryButton(
            text = stringResource(R.string.auth_continue_google),
            onClick = { clickedButton = ClickedButton.GOOGLE; onGoogleClick() },
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading && (clickedButton == ClickedButton.GOOGLE),
            enabled = !isLoading && googleEnabled,
            backgroundColor = colors.surface,
            textColor = colors.onSurface,
            leadingIcon = painterResource(R.drawable.ic_google),
            iconTint = Color.Unspecified,
            border = BorderStroke(
                1.dp,
                colors.outlineVariant,
            ),
        )

        PrimaryButton(
            text = stringResource(R.string.auth_continue_email),
            onClick = { clickedButton = ClickedButton.EMAIL; onEmailClick() },
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading && (clickedButton == ClickedButton.EMAIL),
            enabled = !isLoading,
            leadingIcon = painterResource(R.drawable.ic_mail_24),
        )

        PrimaryButton(
            text = stringResource(R.string.auth_continue_guest),
            onClick = { clickedButton = ClickedButton.GUEST; onGuestClick() },
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading && (clickedButton == ClickedButton.GUEST),
            enabled = !isLoading,
            backgroundColor = colors.surfaceVariant,
            textColor = colors.onSurfaceVariant,
            leadingIcon = painterResource(R.drawable.ic_guest_24),
        )
    }
}

@Preview
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
