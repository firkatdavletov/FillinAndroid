package com.firkat.fillin.feature.launch.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R

@Composable
fun LaunchScreen(
    failed: Boolean,
    onShown: () -> Unit,
    onRetry: () -> Unit,
) {
    LaunchedEffect(Unit) { onShown() }
    Box(Modifier.fillMaxSize().testTag("launch_screen")) {
        LaunchContent(isLoading = !failed)
        if (failed) {
            Column(
                Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.budget_profile_open_failed))
                Button(onClick = onRetry) { Text(stringResource(R.string.budget_profile_retry)) }
            }
        }
    }
}
