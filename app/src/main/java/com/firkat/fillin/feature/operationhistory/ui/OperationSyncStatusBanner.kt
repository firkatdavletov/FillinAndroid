package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.ui.theme.FillinTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun OperationSyncStatusBanner(context: OfflineHistoryContext, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val status =
            when {
                context.syncError != null -> {
                    when (context.syncError) {
                        "scope_changed" -> R.string.operation_sync_scope_changed
                        "unauthorized", "missing_api_token", "access_denied" -> R.string.operation_sync_access_error
                        "idempotency_conflict", "conflict" -> R.string.operation_sync_conflict
                        else -> R.string.operation_sync_retry_error
                    }
                }

                !context.initialSyncComplete -> {
                    R.string.operation_sync_initial
                }

                !context.isOnline -> {
                    R.string.operation_sync_offline
                }

                else -> {
                    null
                }
            }
        status?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (context.syncError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        val syncStatus =
            if (context.isSyncing) {
                stringResource(R.string.operation_sync_running)
            } else {
                context.lastSyncedAt?.let { time ->
                    stringResource(
                        R.string.operation_sync_last,
                        time
                            .atZone(ZoneId.of("Asia/Almaty"))
                            .format(DateTimeFormatter.ofPattern("dd.MM HH:mm")),
                    )
                }.orEmpty()
            }
        Text(
            text = syncStatus,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (context.pendingCount > 0) {
            Text(
                stringResource(R.string.operation_sync_pending_count, context.pendingCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (context.rejectedCount > 0) {
            Text(
                stringResource(R.string.operation_sync_rejected_count, context.rejectedCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview(name = "Initial")
@Preview(name = "Initial dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationSyncInitialPreview() {
    FillinTheme { OperationSyncStatusBanner(OfflineHistoryContext()) }
}

@Preview(name = "Offline")
@Preview(name = "Offline dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationSyncOfflinePreview() {
    FillinTheme { OperationSyncStatusBanner(OfflineHistoryContext(initialSyncComplete = true)) }
}

@Preview(name = "Syncing")
@Preview(name = "Syncing dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationSyncSyncingPreview() {
    FillinTheme { OperationSyncStatusBanner(OfflineHistoryContext(initialSyncComplete = true, isOnline = true, isSyncing = true)) }
}

@Preview(name = "Synced")
@Preview(name = "Synced dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationSyncSyncedPreview() {
    FillinTheme { OperationSyncStatusBanner(OfflineHistoryContext(initialSyncComplete = true, isOnline = true, lastSyncedAt = Instant.parse("2026-10-25T05:00:00Z"))) }
}

@Preview(name = "Error")
@Preview(name = "Error dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationSyncErrorPreview() {
    FillinTheme { OperationSyncStatusBanner(OfflineHistoryContext(initialSyncComplete = true, syncError = "scope_changed")) }
}

@Preview(name = "PendingAndRejected")
@Preview(name = "PendingAndRejected dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationSyncPendingAndRejectedPreview() {
    FillinTheme { OperationSyncStatusBanner(OfflineHistoryContext(initialSyncComplete = true, pendingCount = 2, rejectedCount = 1)) }
}
