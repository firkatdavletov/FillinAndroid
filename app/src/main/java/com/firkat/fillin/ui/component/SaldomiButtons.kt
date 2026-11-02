package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.ui.theme.FillinTheme

/** Standard Material-size buttons, for forms that do not use the large PrimaryButton. */
@Composable
fun SaldomiButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable
fun SaldomiTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable
fun SaldomiOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPreview() { FillinTheme { SaldomiButton({}) { Text("Создать бюджет") } } }
@Preview
@Composable
private fun DisabledButtonPreview() { FillinTheme { SaldomiButton({}, enabled = false) { Text("Создать бюджет") } } }
@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TextButtonPreview() { FillinTheme { SaldomiTextButton({}) { Text("Назад") } } }
@Preview
@Composable
private fun DisabledTextButtonPreview() { FillinTheme { SaldomiTextButton({}, enabled = false) { Text("Назад") } } }
@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OutlinedButtonPreview() { FillinTheme { SaldomiOutlinedButton({}) { Text("Семейный бюджет") } } }
@Preview
@Composable
private fun DisabledOutlinedButtonPreview() { FillinTheme { SaldomiOutlinedButton({}, enabled = false) { Text("Семейный бюджет") } } }
