package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
fun SaldomiProgressIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier)
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ProgressPreview() { FillinTheme { SaldomiProgressIndicator() } }
