package com.firkat.fillin.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigation_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                expandedHeight = 64.dp * LocalDensity.current.fontScale.coerceAtLeast(1f),
            )
        },
        content = content,
    )
}

@Preview(name = "Settings toolbar")
@Preview(name = "Settings toolbar dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScaffoldPreview() { FillinTheme { SettingsScaffold(stringResource(R.string.profile_family_access), {}) {} } }

@Preview(name = "Long settings title", widthDp = 320, fontScale = 2f)
@Composable
private fun SettingsScaffoldLongPreview() { FillinTheme { SettingsScaffold(stringResource(R.string.profile_cloud_sync), {}) {} } }
