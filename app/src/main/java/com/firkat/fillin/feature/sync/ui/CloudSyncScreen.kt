package com.firkat.fillin.feature.sync.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetupload.ui.LocalBudgetUploadSection
import com.firkat.fillin.feature.sync.domain.BudgetSyncContext
import com.firkat.fillin.ui.component.PrimaryButton
import com.firkat.fillin.ui.component.SettingsScaffold
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.icon.FillinIcons
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CloudSyncRoute(onBack: () -> Unit, onSignIn: () -> Unit, viewModel: BudgetSyncViewModel = koinViewModel()) {
    val state by viewModel.context.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    CloudSyncScreen(state, busy, error, onBack, { enabled -> if (enabled) viewModel.enable() else viewModel.disable() }, viewModel::refresh) {
        if (!state.cloudEnabled) LocalBudgetUploadSection(onSignIn = onSignIn)
    }
}

@Composable
fun CloudSyncScreen(
    state: BudgetSyncContext,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onCloudEnabledChange: (Boolean) -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
    uploadContent: @Composable () -> Unit = {},
) {
    SettingsScaffold(stringResource(R.string.profile_cloud_sync), onBack, modifier.testTag("cloud_sync_screen")) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CloudSyncToggle(state.cloudEnabled, !busy && state.ready && !state.syncing, onCloudEnabledChange)
            Text(stringResource(if (state.cloudEnabled) R.string.cloud_sync_enabled_hint else R.string.cloud_sync_disabled_hint), style = MaterialTheme.typography.bodySmall)
            if (busy || state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.cloudEnabled) {
                PrimaryButton(stringResource(R.string.cloud_sync_now), onSyncClick, Modifier.fillMaxWidth(), enabled = !busy && !state.syncing)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cloud_sync_last_label), style = MaterialTheme.typography.bodySmall)
                    val date = state.lastSyncedAt?.let { value ->
                        runCatching { DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru")).withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrNull()
                    }
                    Text(date ?: stringResource(R.string.cloud_sync_never), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.pending > 0) Text(stringResource(R.string.cloud_sync_pending, state.pending), style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(syncErrorText(it), color = MaterialTheme.colorScheme.error) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.issues.forEach { issue ->
                Text(stringResource(R.string.cloud_sync_issue, issue.title, issue.local), color = MaterialTheme.colorScheme.error)
            }
            uploadContent()
        }
    }
}

@Composable
private fun CloudSyncToggle(enabled: Boolean, interactive: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.sync_title)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 88.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(FillinIcons.CloudUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.wrapContentSize()) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(if (enabled) R.string.cloud_sync_on else R.string.cloud_sync_off), style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = interactive,
                modifier = Modifier.testTag("cloud_sync_toggle").semantics { contentDescription = description },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                thumbContent = if (enabled) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }) else null,
            )
        }
    }
}

@Preview(name = "Sync enabled")
@Preview(name = "Sync enabled dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CloudSyncToggleEnabledPreview() { FillinTheme { CloudSyncToggle(true, true, {}) } }

@Preview(name = "Sync disabled")
@Preview(name = "Sync disabled dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CloudSyncToggleDisabledPreview() { FillinTheme { CloudSyncToggle(false, true, {}) } }

@Preview(name = "Sync unavailable")
@Composable
private fun CloudSyncToggleUnavailablePreview() { FillinTheme { CloudSyncToggle(false, false, {}) } }

@Preview(name = "Sync in progress", widthDp = 320, fontScale = 2f)
@Composable
private fun CloudSyncToggleBusyPreview() { FillinTheme { CloudSyncToggle(true, false, {}) } }

@Preview(name = "Cloud screen enabled", widthDp = 393, heightDp = 852)
@Preview(name = "Cloud screen enabled dark", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CloudSyncEnabledPreview() { FillinTheme { CloudSyncScreen(BudgetSyncContext(ready = true, cloudEnabled = true, lastSyncedAt = "2026-10-24T04:41:00Z"), false, null, {}, {}, {}) } }

@Preview(name = "Cloud screen disabled", widthDp = 393, heightDp = 852)
@Composable
private fun CloudSyncDisabledPreview() { FillinTheme { CloudSyncScreen(BudgetSyncContext(ready = true), false, null, {}, {}, {}) } }

@Preview(name = "Cloud screen error", widthDp = 320, fontScale = 2f)
@Composable
private fun CloudSyncErrorPreview() { FillinTheme { CloudSyncScreen(BudgetSyncContext(ready = true, cloudEnabled = true, error = "unauthorized", pending = 3), false, null, {}, {}, {}) } }
