package com.firkat.fillin.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme

@PreviewLightDark
@PreviewFontScale
@Composable
private fun BasicTextFieldEmptyPreview() = BasicTextFieldPreview("")

@PreviewLightDark
@Composable
private fun BasicTextFieldFilledPreview() = BasicTextFieldPreview("Продукты и напитки")

@PreviewLightDark
@Composable
private fun BasicTextFieldDisabledPreview() = BasicTextFieldPreview("Прочее", enabled = false)

@PreviewLightDark
@Composable
private fun BasicTextFieldErrorPreview() = BasicTextFieldPreview(" ", isError = true)

@Composable
private fun BasicTextFieldPreview(
    value: String,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    FillinTheme {
        SaldomiBasicTextField(value, {
        }, stringResource(R.string.categories_name_label), enabled = enabled, isError = isError)
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun RadioOptionsPreview() = RadioOptions(enabled = true)

@PreviewLightDark
@Composable
private fun DisabledRadioOptionsPreview() = RadioOptions(enabled = false)

@Composable
private fun RadioOptions(
    enabled: Boolean
) {
    FillinTheme {
        Column {
            SaldomiRadioButton(stringResource(R.string.categories_kind_expense), true, {}, enabled = enabled)
            SaldomiRadioButton(stringResource(R.string.categories_kind_income), false, {}, enabled = enabled)
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun CategoryActionButtonsPreview() = ActionButtons()

@PreviewLightDark
@Composable
private fun DisabledCategoryActionButtonsPreview() = ActionButtons(enabled = false)

@PreviewLightDark
@Composable
private fun LoadingCategoryActionButtonsPreview() = ActionButtons(loading = true)

@Composable
private fun ActionButtons(
    enabled: Boolean = true,
    loading: Boolean = false
) {
    FillinTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SaldomiDeleteButton({}, enabled = enabled, isLoading = loading) {
                Text(stringResource(R.string.categories_delete_confirm))
            }
            SaldomiPrimaryButton({}, enabled = enabled, isLoading = loading) {
                Text(stringResource(R.string.categories_save_submit))
            }
        }
    }
}
