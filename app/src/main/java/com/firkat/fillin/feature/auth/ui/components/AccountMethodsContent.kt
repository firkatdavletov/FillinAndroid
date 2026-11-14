package com.firkat.fillin.feature.auth.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.ui.component.AccountIdentity
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun AccountMethodsContent(
    user: AuthUser?,
    busy: Boolean,
    error: AuthError?,
    notice: String?,
    onGoogle: () -> Unit,
    onAddPassword: () -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 32.dp).testTag("email_auth"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack, enabled = !busy, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(FillinIcons.Back, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.email_back), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.email_methods),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                AccountIdentity(null, user?.email, Modifier.padding(16.dp))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("methods_loading"))
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
            if (user?.emailVerified ==
                false
            ) {
                TextButton(onClick = onVerify, enabled = !busy) { Text(stringResource(R.string.email_verify)) }
            }
            MethodCard(
                google = true,
                connected = user?.providers?.contains("google.com") == true,
                busy = busy,
                onConnect = onGoogle
            )
            MethodCard(
                google = false,
                connected = user?.providers?.contains("password") == true,
                busy = busy,
                onConnect = onAddPassword
            )
        }
    }
}

@Composable
private fun MethodCard(
    google: Boolean,
    connected: Boolean,
    busy: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(if (google) R.string.email_google_provider else R.string.email_password_method)
    val status = stringResource(
        if (google) {
            if (connected) R.string.email_google_connected else R.string.email_google_missing
        } else {
            if (connected) R.string.email_password_connected else R.string.email_password_missing
        }
    )
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (google) {
                    Icon(painterResource(R.drawable.ic_google), null, Modifier.size(28.dp), tint = Color.Unspecified)
                } else {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(
                    if (connected) Icons.Filled.CheckCircle else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null,
                    tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            if (!connected) {
                Button(
                    onClick = onConnect,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        stringResource(if (google) R.string.email_link_google else R.string.email_add_password),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (google) {
                    Text(
                        stringResource(R.string.email_google_same_email_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    val border = if (connected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    val cardModifier = modifier.fillMaxWidth().testTag(if (google) "method_google" else "method_password")
    if (connected) {
        Surface(
            modifier = cardModifier,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = border,
            content = content
        )
    } else {
        Surface(
            onClick = onConnect,
            enabled = !busy,
            modifier = cardModifier,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            content = content
        )
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun MissingGooglePreview() {
    FillinTheme { MethodCard(true, false, false, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ConnectedGooglePreview() {
    FillinTheme { MethodCard(true, true, false, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun MissingPasswordPreview() {
    FillinTheme { MethodCard(false, false, false, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ConnectedPasswordPreview() {
    FillinTheme { MethodCard(false, true, false, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BusyMethodsPreview() {
    FillinTheme {
        Column {
            MethodCard(true, false, true, {})
            MethodCard(false, false, true, {})
        }
    }
}

@Preview(widthDp = 320, fontScale = 2f)
@Preview(widthDp = 840)
@Composable
private fun LargeMethodsPreview() {
    FillinTheme { MethodCard(true, false, false, {}) }
}
