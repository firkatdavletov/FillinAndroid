package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme

/** An outlined form input whose label remains visible while a value is entered. */
@Composable
fun SaldomiFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth().heightIn(min = 72.dp)
                .border(1.dp, if (error != null) scheme.error else scheme.outlineVariant, RoundedCornerShape(16.dp)),
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = { FormIcon(icon, Modifier.padding(start = 12.dp, end = 8.dp)) },
            isError = error != null,
            keyboardOptions = keyboardOptions,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface,
                disabledContainerColor = scheme.surface,
                errorContainerColor = scheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent
            )
        )
        if (error !=
            null
        ) {
            Text(
                error,
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = scheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** A read-only value using the same visual language as the editable form fields. */
@Composable
fun SaldomiFormValue(
    label: String,
    value: String,
    icon: Painter,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FormIcon(icon)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A single accessible radio option with a title and explanation. */
@Composable
fun SaldomiRadioCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    description: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(
                alpha = 0.6f
            )
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f
                    )
                },
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}

@Composable
private fun FormIcon(
    icon: Painter,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FormFieldPreview() {
    FillinTheme { SaldomiFormTextField("0", {}, "Баланс", painterResource(R.drawable.ic_wallet)) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FormFieldErrorPreview() {
    FillinTheme {
        SaldomiFormTextField("x", {}, "Баланс", painterResource(R.drawable.ic_wallet), error = "Введите сумму")
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FormFieldDisabledPreview() {
    FillinTheme { SaldomiFormTextField("0", {}, "Баланс", painterResource(R.drawable.ic_wallet), enabled = false) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FormValuePreview() {
    FillinTheme { SaldomiFormValue("Валюта", "KZT — тенге", painterResource(R.drawable.ic_wallet)) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RadioCardSelectedPreview() {
    FillinTheme {
        SaldomiRadioCard(true, {}, "В облаке", "Синхронизация между устройствами", painterResource(R.drawable.ic_cloud))
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RadioCardUnselectedPreview() {
    FillinTheme {
        SaldomiRadioCard(false, {
        }, "В облаке", "Синхронизация между устройствами", painterResource(R.drawable.ic_cloud))
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RadioCardDisabledPreview() {
    FillinTheme {
        SaldomiRadioCard(false, {
        }, "В облаке", "Синхронизация между устройствами", painterResource(R.drawable.ic_cloud), enabled = false)
    }
}

@Preview(widthDp = 320, fontScale = 2f)
@Preview(widthDp = 840)
@Composable
private fun RadioCardLargePreview() {
    FillinTheme {
        SaldomiRadioCard(true, {
        }, "В облаке", "Синхронизация между всеми вашими устройствами", painterResource(R.drawable.ic_cloud))
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FormFieldEmptyPreview() {
    FillinTheme { SaldomiFormTextField("", {}, "Название бюджета", painterResource(R.drawable.ic_setup_folder)) }
}

@Preview(widthDp = 320, fontScale = 2f)
@Preview(widthDp = 840)
@Composable
private fun FormFieldLongPreview() {
    FillinTheme {
        SaldomiFormTextField("Бюджет на большую семейную поездку", {
        }, "Название бюджета", painterResource(R.drawable.ic_setup_folder))
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FormIconPreview() {
    FillinTheme { FormIcon(painterResource(R.drawable.ic_wallet)) }
}

@Preview(widthDp = 320, fontScale = 2f)
@Preview(widthDp = 840)
@Composable
private fun FormValueLargePreview() {
    FillinTheme { SaldomiFormValue("Валюта", "KZT — тенге", painterResource(R.drawable.ic_setup_currency)) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RadioCardSelectedDisabledPreview() {
    FillinTheme {
        SaldomiRadioCard(true, {
        }, "В облаке", "Синхронизация между устройствами", painterResource(R.drawable.ic_cloud), enabled = false)
    }
}
