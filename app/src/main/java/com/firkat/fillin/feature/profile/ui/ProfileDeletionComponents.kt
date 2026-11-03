package com.firkat.fillin.feature.profile.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.profile.domain.*
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun DeleteProfileDialog(value: String, onChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier,
        title = { Text(stringResource(R.string.profile_delete)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.profile_delete_warning))
            OutlinedTextField(value, onChange, label = { Text(stringResource(R.string.profile_delete_type)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("delete_confirmation_input"))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_delete_cancel)) } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = value.trim() == "УДАЛИТЬ", colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("delete_confirm")) { Text(stringResource(R.string.profile_delete_forever)) } })
}

@Composable
internal fun DeletionStatusContent(state: ProfileDeletionState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(when (state.phase) {
            DeletionPhase.Completed -> R.string.profile_deleted
            DeletionPhase.Failed -> if (state.error == "account_deletion_in_progress") R.string.profile_delete_conflict else R.string.profile_delete_error
            DeletionPhase.Reauthenticate -> if (state.error == "reauthentication_failed") R.string.profile_delete_identity_error else R.string.profile_delete_reauthenticate
            else -> R.string.profile_delete_pending
        }))
        if (state.phase == DeletionPhase.Working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.phase == DeletionPhase.Failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.budget_profile_retry)) }
    }
}

@Composable
internal fun DeletionReauthentication(password: String, onPassword: (String) -> Unit, passwordAvailable: Boolean, googleAvailable: Boolean, busy: Boolean, onPasswordConfirm: () -> Unit, onGoogle: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (passwordAvailable) {
            OutlinedTextField(password, onPassword, enabled = !busy, label = { Text(stringResource(R.string.email_password)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onPasswordConfirm, enabled = password.isNotEmpty() && !busy) { Text(stringResource(R.string.profile_delete_confirm_identity)) }
        }
        if (googleAvailable) OutlinedButton(onClick = onGoogle, enabled = !busy) { Text(stringResource(R.string.auth_google_account)) }
    }
}

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Preview(fontScale = 2f, widthDp = 320)
@Composable private fun DeleteDialogPreview() { FillinTheme { DeleteProfileDialog("", {}, {}, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeleteDialogConfirmedPreview() { FillinTheme { DeleteProfileDialog("УДАЛИТЬ", {}, {}, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionWorkingPreview() { FillinTheme { DeletionStatusContent(ProfileDeletionState(DeletionPhase.Working), {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionPendingPreview() { FillinTheme { DeletionStatusContent(ProfileDeletionState(DeletionPhase.Pending), {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionFailedPreview() { FillinTheme { DeletionStatusContent(ProfileDeletionState(DeletionPhase.Failed), {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionConflictPreview() { FillinTheme { DeletionStatusContent(ProfileDeletionState(DeletionPhase.Failed, "account_deletion_in_progress"), {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionCompletedPreview() { FillinTheme { DeletionStatusContent(ProfileDeletionState(DeletionPhase.Completed), {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionReauthStatusPreview() { FillinTheme { DeletionStatusContent(ProfileDeletionState(DeletionPhase.Reauthenticate), {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionPasswordPreview() { FillinTheme { DeletionReauthentication("", {}, true, false, false, {}, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionGooglePreview() { FillinTheme { DeletionReauthentication("", {}, false, true, false, {}, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionBothPreview() { FillinTheme { DeletionReauthentication("password", {}, true, true, false, {}, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionBusyPreview() { FillinTheme { DeletionReauthentication("password", {}, true, true, true, {}, {}) } }

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletionIdentityErrorPreview() { FillinTheme { DeletionStatusContent(ProfileDeletionState(DeletionPhase.Reauthenticate, "reauthentication_failed"), {}) } }
