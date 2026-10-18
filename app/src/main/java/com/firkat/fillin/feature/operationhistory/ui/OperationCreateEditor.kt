package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OperationCreateEditor(
    editor: OperationCreateEditorUiState,
    context: OfflineHistoryContext,
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    var categoriesExpanded by rememberSaveable { mutableStateOf(false) }
    var datePickerVisible by rememberSaveable { mutableStateOf(false) }
    val kind = if (editor.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income
    val categories = context.categories.filter { it.kind == kind }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.operation_create_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OperationType.entries.forEach { type ->
                FilterChip(
                    selected = editor.type == type,
                    enabled = !editor.isSaving,
                    onClick = { onEvent(OperationHistoryEvent.CreateTypeChanged(type)) },
                    label = {
                        Text(
                            stringResource(
                                if (type ==
                                    OperationType.Expense
                                ) {
                                    R.string.planned_operations_expense
                                } else {
                                    R.string.planned_operations_income
                                },
                            ),
                        )
                    },
                )
            }
        }
        OutlinedTextField(
            value = editor.amount,
            onValueChange = { onEvent(OperationHistoryEvent.CreateAmountChanged(it)) },
            label = { Text(stringResource(R.string.operation_create_amount)) },
            singleLine = true,
            enabled = !editor.isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("operation-create-amount"),
        )
        Box {
            OutlinedButton(
                onClick = { categoriesExpanded = true },
                enabled = !editor.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("operation-create-category"),
            ) {
                Text(categories.firstOrNull { it.id == editor.categoryId }?.name ?: stringResource(R.string.operation_create_category))
            }
            DropdownMenu(expanded = categoriesExpanded, onDismissRequest = { categoriesExpanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(text = { Text("${category.icon.emoji} ${category.name}") }, onClick = {
                        onEvent(OperationHistoryEvent.CreateCategoryChanged(category.id))
                        categoriesExpanded = false
                    })
                }
            }
        }
        if (categories.isEmpty()) Text(stringResource(R.string.operation_create_no_categories))
        OutlinedButton(onClick = { datePickerVisible = true }, enabled = !editor.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.operation_create_date, editor.occurredOn.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))))
        }
        OutlinedTextField(
            value = editor.description,
            onValueChange = { onEvent(OperationHistoryEvent.CreateDescriptionChanged(it)) },
            enabled = !editor.isSaving,
            label = { Text(stringResource(R.string.operation_create_description)) },
            supportingText = { Text("${editor.description.length}/160") },
            modifier = Modifier.fillMaxWidth().testTag("operation-create-description"),
        )
        editor.error?.let { Text(stringResource(R.string.operation_create_error), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { onEvent(OperationHistoryEvent.CreateSaved) },
            enabled = editor.canSave && categories.any { it.id == editor.categoryId },
            modifier = Modifier.fillMaxWidth().testTag("operation-create-save"),
        ) {
            Text(stringResource(R.string.operation_create_save))
        }
        TextButton(
            onClick = { onEvent(OperationHistoryEvent.CreateClosed) },
            enabled = !editor.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.planned_operations_cancel))
        }
    }
    if (datePickerVisible) {
        val today = LocalDate.now(ZoneId.of("Asia/Almaty"))
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis = editor.occurredOn.toPickerMillis(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            val date = pickerMillisToDate(utcTimeMillis)
                            return date <= today && (context.accountingStartedOn?.let { date >= it } ?: true)
                        }
                    },
            )
        DatePickerDialog(onDismissRequest = { datePickerVisible = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onEvent(OperationHistoryEvent.CreateDateChanged(pickerMillisToDate(it))) }
                datePickerVisible = false
            }) { Text(stringResource(R.string.operation_history_apply)) }
        }, dismissButton = {
            TextButton(onClick = { datePickerVisible = false }) { Text(stringResource(R.string.planned_operations_cancel)) }
        }) { DatePicker(state = picker) }
    }
}

@Composable
internal fun OperationSyncStatusBanner(context: OfflineHistoryContext) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val status =
            when {
                context.syncError != null -> {
                    when (context.syncError) {
                        "scope_changed" -> R.string.operation_sync_scope_changed
                        "unauthorized", "missing_api_token", "access_denied" -> R.string.operation_sync_access_error
                        "idempotency_conflict", "conflict" -> R.string.operation_sync_conflict
                        else -> R.string.operation_sync_retry_error
                    }
                }

                !context.initialSyncComplete -> {
                    R.string.operation_sync_initial
                }

                !context.isOnline -> {
                    R.string.operation_sync_offline
                }

                else -> {
                    null
                }
            }
        status?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (context.syncError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        val syncStatus =
            if (context.isSyncing) {
                stringResource(R.string.operation_sync_running)
            } else {
                context.lastSyncedAt?.let { time ->
                    stringResource(
                        R.string.operation_sync_last,
                        time
                            .atZone(ZoneId.of("Asia/Almaty"))
                            .format(DateTimeFormatter.ofPattern("dd.MM HH:mm")),
                    )
                }.orEmpty()
            }
        Text(
            text = syncStatus,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (context.pendingCount > 0) {
            Text(
                stringResource(R.string.operation_sync_pending_count, context.pendingCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (context.rejectedCount > 0) {
            Text(
                stringResource(R.string.operation_sync_rejected_count, context.rejectedCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
