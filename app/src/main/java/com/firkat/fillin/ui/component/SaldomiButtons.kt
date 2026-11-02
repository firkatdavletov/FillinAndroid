package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.ui.theme.FillinTheme

/** Standard Material-size buttons, for forms that do not use the large PrimaryButton. */
@Composable
fun SaldomiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    isLoading: Boolean = false,
    leadingIcon: Painter? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = if (large) modifier.heightIn(min = 56.dp) else modifier,
        enabled = enabled && !isLoading,
        shape = if (large) RoundedCornerShape(16.dp) else ButtonDefaults.shape,
        contentPadding = if (large) PaddingValues(horizontal = 20.dp, vertical = 16.dp) else ButtonDefaults.ContentPadding,
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(12.dp))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        ProvideTextStyle(if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge) { content() }
    }
}

@Composable
fun SaldomiTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable
fun SaldomiOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    isLoading: Boolean = false,
    leadingIcon: Painter? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        border = if (large) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder(enabled),
        colors = if (large) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface) else ButtonDefaults.outlinedButtonColors(),
        modifier = if (large) modifier.heightIn(min = 56.dp) else modifier,
        enabled = enabled && !isLoading,
        shape = if (large) RoundedCornerShape(16.dp) else ButtonDefaults.shape,
        contentPadding = if (large) PaddingValues(horizontal = 20.dp, vertical = 16.dp) else ButtonDefaults.ContentPadding,
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(12.dp))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        ProvideTextStyle(if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge) { content() }
    }
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

@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun LargeButtonPreview() { FillinTheme { SaldomiButton({}, large = true) { Text("Создать бюджет") } } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun LargeButtonLoadingPreview() { FillinTheme { SaldomiButton({}, large = true, isLoading = true) { Text("Создать бюджет") } } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun LargeButtonDisabledPreview() { FillinTheme { SaldomiButton({}, large = true, enabled = false) { Text("Создать бюджет") } } }
@Preview(widthDp = 320, fontScale = 2f) @Preview(widthDp = 840)
@Composable private fun LargeOutlinedButtonLongPreview() { FillinTheme { SaldomiOutlinedButton({}, large = true, leadingIcon = com.firkat.fillin.ui.icon.FillinIcons.FamilyAccess) { Text("Вступить в семейный бюджет") } } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun LargeOutlinedButtonLoadingPreview() { FillinTheme { SaldomiOutlinedButton({}, large = true, isLoading = true) { Text("Создать бюджет") } } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable private fun LargeOutlinedButtonDisabledPreview() { FillinTheme { SaldomiOutlinedButton({}, large = true, enabled = false) { Text("Вступить в семейный бюджет") } } }
