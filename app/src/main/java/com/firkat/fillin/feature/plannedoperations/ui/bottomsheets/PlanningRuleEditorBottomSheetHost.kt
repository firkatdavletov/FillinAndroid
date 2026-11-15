package com.firkat.fillin.feature.plannedoperations.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.ui.bottomsheets.CategorySelectionSheet
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.feature.plannedoperations.ui.RuleWindow
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.theme.FillinTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
internal fun PlanningRuleEditorBottomSheetHost(
    window: RuleWindow?,
    type: PlannedOperationType,
    categoryId: String?,
    categories: List<Category>,
    categoriesLoading: Boolean,
    categoriesError: Boolean,
    frequency: PlanningFrequency,
    date: LocalDate,
    accountingStart: LocalDate,
    originalDate: LocalDate?,
    canEdit: Boolean,
    canDiscard: Boolean,
    canDelete: Boolean,
    onCategorySelected: (String) -> Unit,
    onRetryCategories: () -> Unit,
    onFrequencySelected: (PlanningFrequency) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (window) {
        RuleWindow.Category -> {
            val kind = if (type == PlannedOperationType.Expense) CategoryKind.Expense else CategoryKind.Income
            CategorySelectionSheet(
                categories = categories.filter { it.kind == kind },
                selectedId = categoryId,
                loading = categoriesLoading,
                hasError = categoriesError,
                onSelect = onCategorySelected,
                onDismiss = onDismiss,
                onRetry = onRetryCategories,
                modifier = modifier
            )
        }
        RuleWindow.Frequency -> SelectFrequencyBottomSheet(
            type = type,
            selectedFrequency = frequency,
            onSelect = onFrequencySelected,
            onDismiss = onDismiss,
            modifier = modifier,
            enabled = canEdit
        )
        RuleWindow.Date -> RuleDateBottomSheet(
            date,
            accountingStart,
            originalDate,
            canEdit,
            onDateSelected,
            onDismiss,
            modifier
        )
        RuleWindow.Discard -> RuleConfirmationBottomSheet(
            RuleWindow.Discard,
            canDiscard,
            onDiscard,
            onDismiss,
            modifier
        )
        RuleWindow.Delete -> RuleConfirmationBottomSheet(
            RuleWindow.Delete,
            canDelete,
            onDelete,
            onDismiss,
            modifier
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDateBottomSheet(
    date: LocalDate,
    accountingStart: LocalDate,
    originalDate: LocalDate?,
    enabled: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minimumDate = maxOf(accountingStart, LocalDate.now(ZoneId.of("Asia/Almaty")))
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(
                utcTimeMillis: Long
            ): Boolean {
                val candidate = pickerDate(utcTimeMillis)
                return candidate >= minimumDate || candidate == originalDate
            }
        }
    )
    SaldomiActionBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        confirmButton = {
            SaldomiButton(
                onClick = {
                    picker.selectedDateMillis?.let { onDateSelected(pickerDate(it)) }
                    onDismiss()
                },
                enabled = enabled && picker.selectedDateMillis != null,
                modifier = Modifier.fillMaxWidth(),
                large = true
            ) {
                Text(
                    text = stringResource(R.string.rule_select)
                )
            }
        },
        dismissButton = { CancelRuleButton(onDismiss) },
        edgeToEdgeContent = true,
        text = {
            DatePicker(
                state = picker,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    )
}

private fun pickerDate(
    millis: Long
): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun RuleConfirmationBottomSheet(
    window: RuleWindow,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deleting = window == RuleWindow.Delete
    SaldomiActionBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (deleting) R.string.rule_delete_question else R.string.rule_discard_question))
        },
        confirmButton = {
            SaldomiButton(
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                large = true,
                destructive = deleting
            ) { Text(stringResource(R.string.rule_confirm)) }
        },
        dismissButton = { CancelRuleButton(onDismiss) }
    )
}

@Composable
private fun CancelRuleButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiOutlinedButton(onDismiss, modifier.fillMaxWidth(), large = true) {
        Text(stringResource(R.string.rule_cancel))
    }
}

@Preview(name = "Date sheet")
@Preview(name = "Date sheet dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Date sheet compact", widthDp = 320, heightDp = 600, fontScale = 2f)
@Composable
private fun RuleDateSheetPreview() {
    FillinTheme {
        RuleDateBottomSheet(LocalDate.now(), LocalDate.MIN, null, true, {}, {})
    }
}

@Preview(name = "Discard sheet")
@Preview(name = "Discard sheet dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Discard sheet compact", widthDp = 320, heightDp = 600, fontScale = 2f)
@Composable
private fun RuleDiscardSheetPreview() {
    FillinTheme { RuleConfirmationBottomSheet(RuleWindow.Discard, true, {}, {}) }
}

@Preview(name = "Delete sheet")
@Preview(name = "Delete sheet dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Delete sheet compact", widthDp = 320, heightDp = 600, fontScale = 2f)
@Composable
private fun RuleDeleteSheetPreview() {
    FillinTheme { RuleConfirmationBottomSheet(RuleWindow.Delete, true, {}, {}) }
}

@Preview(name = "Date sheet disabled")
@Preview(name = "Date sheet disabled dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RuleDateSheetDisabledPreview() {
    FillinTheme { RuleDateBottomSheet(LocalDate.now(), LocalDate.MIN, null, false, {}, {}) }
}

@Preview(name = "Discard sheet disabled")
@Preview(name = "Discard sheet disabled dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RuleDiscardSheetDisabledPreview() {
    FillinTheme { RuleConfirmationBottomSheet(RuleWindow.Discard, false, {}, {}) }
}

@Preview(name = "Delete sheet disabled")
@Preview(name = "Delete sheet disabled dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RuleDeleteSheetDisabledPreview() {
    FillinTheme { RuleConfirmationBottomSheet(RuleWindow.Delete, false, {}, {}) }
}
