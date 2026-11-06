package com.firkat.fillin.feature.appaccess.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.R
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AboutAppViewModel(private val access: AppAccess) : ViewModel() {
    private val dialog = MutableStateFlow<String?>(null)
    val installationId = dialog.asStateFlow()
    val checking = access.checking
    private val result = MutableStateFlow<Boolean?>(null)
    val refreshResult = result.asStateFlow()

    fun open() {
        viewModelScope.launch(Dispatchers.IO) {
            dialog.value = runCatching { access.installationId() }.getOrDefault("")
        }
    }

    fun close() {
        dialog.value = null
        result.value = null
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { result.value = access.refresh(manual = true) }
    }
}

@Composable
internal fun AboutAppRoute(viewModel: AboutAppViewModel) {
    val id by viewModel.installationId.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val result by viewModel.refreshResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    id?.let { installationId ->
        val label = stringResource(R.string.app_installation_id)
        AboutAppDialog(
            version = BuildConfig.VERSION_NAME,
            id = installationId,
            checking = checking,
            refreshResult = result,
            onCopy = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(label, installationId))
            },
            onRefresh = viewModel::refresh,
            onClose = viewModel::close,
        )
    }
}

@Composable
private fun AboutAppDialog(
    version: String,
    id: String,
    checking: Boolean,
    refreshResult: Boolean?,
    onCopy: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.app_about)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_version, version))
                Text(stringResource(R.string.app_installation_id))
                Text(id.ifEmpty { stringResource(R.string.app_installation_unavailable) })
                TextButton(onClick = onCopy, enabled = id.isNotEmpty()) {
                    Text(stringResource(R.string.app_copy_installation_id))
                }
                if (checking || refreshResult != null) {
                    Text(stringResource(when {
                        checking -> R.string.app_access_checking
                        refreshResult == true -> R.string.app_access_checked
                        else -> R.string.app_access_check_failed
                    }))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !checking) {
                Text(stringResource(R.string.app_access_check))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.app_about_close)) } },
    )
}

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun AboutAppDefaultPreview() { AboutPreview() }

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun AboutAppCheckingPreview() { AboutPreview(checking = true) }

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun AboutAppCheckedPreview() { AboutPreview(result = true) }

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun AboutAppFailedPreview() { AboutPreview(result = false) }

@Preview(widthDp = 320, fontScale = 2f) @Preview(widthDp = 840, uiMode = UI_MODE_NIGHT_YES)
@Composable private fun AboutAppUnavailablePreview() { AboutPreview(id = "") }

@Composable
private fun AboutPreview(
    checking: Boolean = false,
    result: Boolean? = null,
    id: String = "a625153f-4be5-47b1-adfa-2c404603c034",
) {
    FillinTheme { AboutAppDialog("0.1", id, checking, result, {}, {}, {}) }
}
