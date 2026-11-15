package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme

/** Compact, single-line outlined input. Its caller owns the text and validation state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaldomiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    label: String? = null
) {
    if (label != null) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            enabled = enabled,
            isError = isError,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )
        return
    }
    val interactions = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surface,
        errorContainerColor = scheme.surface,
        unfocusedBorderColor = scheme.outlineVariant
    )
    val errorDescription = stringResource(R.string.saldomi_text_field_error)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 54.dp)
            .semantics {
                if (isError) error(errorDescription)
            },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = scheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        ),
        cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactions,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactions,
                isError = isError,
                placeholder = placeholder?.let { hint -> { Text(hint, style = MaterialTheme.typography.bodyLarge) } },
                colors = colors,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactions,
                        colors = colors,
                        shape = RoundedCornerShape(14.dp),
                        focusedBorderThickness = 1.dp
                    )
                }
            )
        }
    )
}

@Preview(name = "Empty")
@Preview(name = "Empty dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SaldomiTextFieldEmptyPreview() {
    FillinTheme { SaldomiTextField("", {}, placeholder = stringResource(R.string.operation_create_placeholder)) }
}

@Preview(name = "Filled")
@Preview(name = "Filled dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SaldomiTextFieldFilledPreview() {
    FillinTheme { SaldomiTextField("Продукты 1200", {}) }
}

@Preview(name = "Error")
@Preview(name = "Error dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SaldomiTextFieldErrorPreview() {
    FillinTheme { SaldomiTextField("Продукты", {}, isError = true) }
}

@Preview(name = "Disabled")
@Preview(name = "Disabled dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SaldomiTextFieldDisabledPreview() {
    FillinTheme { SaldomiTextField("Продукты 1200", {}, enabled = false) }
}

@Preview(name = "Compact, long input", widthDp = 320)
@Preview(name = "Large font", widthDp = 320, fontScale = 2f)
@Composable
private fun SaldomiTextFieldLongPreview() {
    FillinTheme { SaldomiTextField("Подставка для ног и рабочего кресла 5000", {}) }
}

@Preview(name = "Label empty")
@Preview(name = "Label dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LabelEmptyPreview() {
    FillinTheme { SaldomiTextField("", {}, label = "Название бюджета") }
}

@Preview(name = "Label filled")
@Composable
private fun LabelFilledPreview() {
    FillinTheme { SaldomiTextField("Семья", {}, label = "Название бюджета") }
}

@Preview(name = "Label disabled")
@Composable
private fun LabelDisabledPreview() {
    FillinTheme { SaldomiTextField("Семья", {}, label = "Название бюджета", enabled = false) }
}

@Preview(name = "Label error")
@Composable
private fun LabelErrorPreview() {
    FillinTheme { SaldomiTextField("?", {}, label = "Баланс", isError = true) }
}

@Preview(name = "Label large text", widthDp = 320, fontScale = 2f)
@Composable
private fun LabelLargePreview() {
    FillinTheme { SaldomiTextField("Большой семейный бюджет", {}, label = "Название бюджета") }
}
