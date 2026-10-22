package com.firkat.fillin.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SaldomiSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
        snackbar = { data ->
            SaldomiSnackbar(data = data)
        },
    )
}

suspend fun SnackbarHostState.showSaldomiSnackbar(
    message: String,
    type: SnackbarType = SnackbarType.Info,
    actionLabel: String? = null,
    duration: SnackbarDuration = if (actionLabel != null) {
        SnackbarDuration.Indefinite
    } else {
        SnackbarDuration.Short
    },
): SnackbarResult {
    return showSnackbar(
        visuals = SaldomiSnackbarVisuals(
            message = message,
            type = type,
            actionLabel = actionLabel,
            duration = duration,
        ),
    )
}