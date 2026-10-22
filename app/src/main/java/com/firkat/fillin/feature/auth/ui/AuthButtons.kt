package com.firkat.fillin.feature.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
) {
    val colors = MaterialTheme.colorScheme
    var clickedButton: ClickedButton = rememberSaveable(Unit) { ClickedButton.GOOGLE }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrimaryButton(
            text = "Продолжить с Google",
            onClick = onGoogleClick,
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading && (clickedButton == ClickedButton.GOOGLE),
            enabled = !isLoading,
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
            text = "Продолжить с email",
            onClick = onEmailClick,
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading && (clickedButton == ClickedButton.EMAIL),
            enabled = !isLoading,
            leadingIcon = painterResource(R.drawable.ic_mail_24),
        )

        PrimaryButton(
            text = "Продолжить без авторизации",
            onClick = onGuestClick,
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