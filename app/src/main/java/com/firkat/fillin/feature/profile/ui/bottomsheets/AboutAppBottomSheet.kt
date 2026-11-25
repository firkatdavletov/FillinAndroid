package com.firkat.fillin.feature.profile.ui.bottomsheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.R
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun AboutAppBottomSheet(
    id: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    version: String = BuildConfig.VERSION_NAME
) {
    val context = LocalContext.current
    val label = stringResource(R.string.app_installation_id)
    SaldomiActionBottomSheet(
        modifier = modifier,
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.app_about)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_version, version))
                Text(stringResource(R.string.app_installation_id))
                Text(id.ifEmpty { stringResource(R.string.app_installation_unavailable) })
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText(label, id))
                    },
                    enabled = id.isNotEmpty()
                ) {
                    Text(stringResource(R.string.app_copy_installation_id))
                }
            }
        },
        confirmButton = {
            SaldomiButton(onClick = onClose, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.app_about_close))
            }
        }
    )
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AboutAppDefaultPreview() {
    AboutPreview()
}

@Preview(widthDp = 320, fontScale = 2f)
@Preview(widthDp = 840, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AboutAppUnavailablePreview() {
    AboutPreview(id = "")
}

@Composable
private fun AboutPreview(
    id: String = "a625153f-4be5-47b1-adfa-2c404603c034"
) {
    FillinTheme { AboutAppBottomSheet(id, onClose = {}, version = "0.1") }
}
