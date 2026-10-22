package com.firkat.fillin.feature.auth.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.component.SaldomiSnackbarHost
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
fun AuthContent(
    modifier: Modifier = Modifier,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
    isLoading: Boolean = false,
    onGoogleClick: () -> Unit = {},
    onEmailClick: () -> Unit = {},
    onGuestClick: () -> Unit = {},
    googleEnabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(
                R.drawable.saldomi_auth_background
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Image(
            painter = painterResource(
                R.drawable.saldomi_logo_stacked
            ),
            contentDescription = "Saldomi",
            modifier = Modifier
                .width(220.dp)
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
            contentScale = ContentScale.Fit
        )
        AuthButtons(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .align(Alignment.BottomCenter),
            isLoading = isLoading,
            googleEnabled = googleEnabled,
            onGoogleClick = onGoogleClick,
            onEmailClick = onEmailClick,
            onGuestClick = onGuestClick
        )
        SaldomiSnackbarHost(
            modifier = Modifier.systemBarsPadding(),
            hostState = snackBarHostState
        )
    }
}

@Preview(
    uiMode = UI_MODE_NIGHT_YES
)
@Composable
private fun AuthContent_Preview_NightMode() {
    FillinTheme {
        AuthContent()
    }
}

@Preview()
@Composable
private fun AuthContent_Preview() {
    FillinTheme {
        AuthContent()
    }
}
