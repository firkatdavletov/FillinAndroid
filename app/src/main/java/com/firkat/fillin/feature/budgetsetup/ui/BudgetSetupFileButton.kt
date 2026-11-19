package com.firkat.fillin.feature.budgetsetup.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.R
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun BudgetSetupFileButton(
    enabled: Boolean,
    working: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiOutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("setup_create_from_file"),
        enabled = enabled,
        large = true,
        isLoading = working
    ) {
        Text(stringResource(R.string.budget_setup_create_from_file))
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun FileButtonPreview() {
    FillinTheme { BudgetSetupFileButton(enabled = true, working = false, onClick = {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DisabledFileButtonPreview() {
    FillinTheme { BudgetSetupFileButton(enabled = false, working = false, onClick = {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ImportingFileButtonPreview() {
    FillinTheme { BudgetSetupFileButton(enabled = false, working = true, onClick = {}) }
}
