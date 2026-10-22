package com.firkat.fillin.feature.launch.presentation

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
fun LaunchContent(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
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
        if (isLoading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
            )
        }
    }
}

@Preview(
    uiMode = UI_MODE_NIGHT_YES
)
@Composable
private fun LaunchContent_Preview_NightMode() {
    FillinTheme {
        LaunchContent()
    }
}

@Preview()
@Composable
private fun LaunchContent_Preview() {
    FillinTheme {
        LaunchContent()
    }
}
