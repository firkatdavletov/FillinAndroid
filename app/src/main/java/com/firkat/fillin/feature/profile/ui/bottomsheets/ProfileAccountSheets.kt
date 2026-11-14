@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firkat.fillin.feature.profile.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.component.SaldomiBottomSheet
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun SignOutAccountBottomSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("sign_out_sheet")) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AccountConfirmationHeader(
                Icons.AutoMirrored.Outlined.Logout,
                stringResource(R.string.profile_sign_out_title),
                false
            )
            Text(
                stringResource(R.string.profile_sign_out_description),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("sign_out_confirm")
                ) {
                    Text(stringResource(R.string.profile_sign_out_confirm))
                }
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.profile_delete_cancel))
                }
            }
        }
    }
}

@Composable
internal fun DeleteAccountBottomSheet(
    value: String,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("delete_account_sheet")) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AccountConfirmationHeader(Icons.Outlined.DeleteOutline, stringResource(R.string.profile_delete_title), true)
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                    Text(
                        stringResource(R.string.profile_delete_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_delete_instruction), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    label = { Text(stringResource(R.string.profile_delete_input_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("delete_confirmation_input")
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.profile_delete_cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = value.trim() == "УДАЛИТЬ",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("delete_confirm")
                ) { Text(stringResource(R.string.profile_delete_confirm)) }
            }
        }
    }
}

@Composable
private fun AccountConfirmationHeader(
    icon: ImageVector,
    title: String,
    destructive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (destructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        ) { Icon(icon, null, Modifier.padding(20.dp).size(36.dp)) }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(name = "Sign out")
@Preview(name = "Sign out dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Sign out large text", widthDp = 320, fontScale = 2f)
@Preview(name = "Sign out wide", widthDp = 840)
@Composable
private fun SignOutSheetPreview() {
    FillinTheme { SignOutAccountBottomSheet({}, {}) }
}

@Preview(name = "Delete empty")
@Preview(name = "Delete empty dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Delete large text", widthDp = 320, fontScale = 2f)
@Preview(name = "Delete wide", widthDp = 840)
@Composable
private fun DeleteSheetPreview() {
    FillinTheme { DeleteAccountBottomSheet("", {}, {}, {}) }
}

@Preview(name = "Delete confirmed")
@Preview(name = "Delete confirmed dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeleteSheetConfirmedPreview() {
    FillinTheme { DeleteAccountBottomSheet("УДАЛИТЬ", {}, {}, {}) }
}

@Preview(name = "Delete invalid")
@Preview(name = "Delete invalid dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeleteSheetInvalidPreview() {
    FillinTheme { DeleteAccountBottomSheet("удалить", {}, {}, {}) }
}

@Preview(name = "Sign out header")
@Preview(name = "Sign out header dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SignOutHeaderPreview() {
    FillinTheme {
        AccountConfirmationHeader(
            Icons.AutoMirrored.Outlined.Logout,
            stringResource(R.string.profile_sign_out_title),
            false
        )
    }
}

@Preview(name = "Delete header")
@Preview(name = "Delete header dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Header large text", widthDp = 320, fontScale = 2f)
@Composable
private fun DeleteHeaderPreview() {
    FillinTheme {
        AccountConfirmationHeader(Icons.Outlined.DeleteOutline, stringResource(R.string.profile_delete_title), true)
    }
}
