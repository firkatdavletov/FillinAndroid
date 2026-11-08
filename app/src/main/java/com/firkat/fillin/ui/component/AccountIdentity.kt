package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun AccountIdentity(name: String?, email: String?, modifier: Modifier = Modifier) {
    val cleanName = name?.takeIf(String::isNotBlank)
    val cleanEmail = email?.takeIf(String::isNotBlank)
    val title = cleanName ?: cleanEmail ?: stringResource(R.string.email_methods)
    val initials = if (cleanName != null) cleanName.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        else title.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    val fontScale = LocalDensity.current.fontScale
    val avatar: @Composable () -> Unit = {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp * fontScale.coerceIn(1f, 1.5f))) {
            Box(contentAlignment = Alignment.Center) { Text(initials, style = MaterialTheme.typography.titleLarge) }
        }
    }
    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (cleanName != null && cleanEmail != null && cleanName != cleanEmail) Text(cleanEmail, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (fontScale >= 1.5f) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            avatar()
            details()
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            avatar()
            Box(Modifier.weight(1f)) { details() }
        }
    }
}

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun NamedAccountPreview() { FillinTheme { AccountIdentity("Анна Иванова", "anna@example.com") } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EmailAccountPreview() { FillinTheme { AccountIdentity(null, "anna@example.com") } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EmptyAccountPreview() { FillinTheme { AccountIdentity(null, null) } }
@Preview(widthDp = 320, fontScale = 2f) @Preview(widthDp = 840)
@Composable private fun LongAccountPreview() { FillinTheme { AccountIdentity("Анна Александровна Иванова", "anna.alexandrovna@example.com") } }
