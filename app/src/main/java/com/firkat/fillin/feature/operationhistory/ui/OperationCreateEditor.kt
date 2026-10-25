package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError
import com.firkat.fillin.ui.component.SaldomiSnackbarHost
import com.firkat.fillin.ui.component.SaldomiTextField
import com.firkat.fillin.ui.component.SnackbarType
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun OperationCreateEditor(
    editor: OperationCreateEditorUiState,
    context: OfflineHistoryContext,
    onEvent: (OperationHistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val kind = if (editor.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income
    val categories = context.categories.filter { it.kind == kind }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    val preview = LocalInspectionMode.current
    val snackbar = remember { SnackbarHostState() }
    val feedbackMessage = editor.feedback?.let {
        stringResource(when (it) {
            OperationCreateFeedback.InvalidInput -> when (editor.inputError) {
                OperationInputError.DescriptionTooLong -> R.string.operation_create_description_too_long
                OperationInputError.AmbiguousAmount -> R.string.operation_create_ambiguous_amount
                else -> R.string.operation_create_invalid_input
            }
            OperationCreateFeedback.InvalidCategory -> R.string.operation_create_invalid_category
            OperationCreateFeedback.Unavailable -> R.string.operation_create_unavailable
            OperationCreateFeedback.SaveFailed -> R.string.operation_create_save_failed
        })
    }
    LaunchedEffect(editor.feedbackSequence) {
        if (feedbackMessage != null) {
            onEvent(OperationHistoryEvent.CreateFeedbackShown(editor.feedbackSequence))
            snackbar.currentSnackbarData?.dismiss()
            if (!editor.isSaving) focusRequester.requestFocus()
            snackbar.showSaldomiSnackbar(feedbackMessage, type = SnackbarType.Error)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        LaunchedEffect(Unit) {
            if (!preview) {
                snapshotFlow { window.isWindowFocused }.first { it }
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
        val compact = maxHeight < 320.dp
        Column(Modifier.fillMaxSize().padding(bottom = if (compact) 8.dp else 16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = if (compact) 8.dp else 16.dp)) {
                        if (showTitle) {
                            Text(stringResource(R.string.operation_record_title), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(24.dp))
                        }
                        SaldomiTextField(
                            value = editor.input,
                            onValueChange = { onEvent(OperationHistoryEvent.CreateInputChanged(it)) },
                            placeholder = stringResource(R.string.operation_create_placeholder),
                            enabled = !editor.isSaving,
                            isError = editor.inputError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("operation-create-input"),
                        )
                        Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                        OperationTypeTabs(
                            type = editor.type,
                            enabled = !editor.isSaving,
                            onTypeClick = { onEvent(OperationHistoryEvent.CreateTypeChanged(it)) },
                        )
                    }
                    Text(
                        stringResource(R.string.operation_create_quick_add),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = if (compact) 8.dp else 16.dp),
                    )
                }
                SaldomiSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.operation_create_no_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("operation-create-no-categories"),
                )
            } else {
                key(editor.type) {
                    OperationCategoryRow(
                        categories = categories,
                        enabled = !editor.isSaving,
                        onCategoryClick = { onEvent(OperationHistoryEvent.CreateCategoryClicked(it)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationTypeTabs(
    type: OperationType,
    enabled: Boolean,
    onTypeClick: (OperationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    PrimaryTabRow(
        selectedTabIndex = OperationType.entries.indexOf(type),
        modifier = modifier.fillMaxWidth().clip(CircleShape).background(scheme.surfaceVariant),
        containerColor = scheme.surfaceVariant,
        indicator = {},
        divider = {},
    ) {
        OperationType.entries.forEach { item ->
            val selected = item == type
            Tab(
                selected = selected,
                enabled = enabled,
                onClick = { onTypeClick(item) },
                selectedContentColor = scheme.onPrimary,
                unselectedContentColor = scheme.onSurface,
                modifier = Modifier.clip(CircleShape)
                    .background(if (selected) scheme.primary else scheme.surfaceVariant)
                    .testTag("operation-create-type-" + item.apiValue),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item == OperationType.Expense) {
                            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(painterResource(R.drawable.ic_add_24), contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Text(stringResource(if (item == OperationType.Expense) R.string.planned_operations_expense else R.string.planned_operations_income))
                    }
                },
            )
        }
    }
}

@Composable
private fun OperationCategoryRow(
    categories: List<Category>,
    enabled: Boolean,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        state = rememberLazyListState(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.fillMaxWidth().testTag("operation-create-categories"),
    ) {
        items(categories, key = { it.id }) { category ->
            OperationCategoryCard(category.name, category.icon.emoji, enabled, { onCategoryClick(category.id) },
                Modifier.testTag("operation-create-category-" + category.id))
        }
    }
}

@Composable
private fun OperationCategoryCard(
    name: String,
    emoji: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.widthIn(min = 80.dp, max = 128.dp).heightIn(min = 88.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Category emoji is an icon: keep its physical size when the text font scales.
            Text(emoji, fontSize = with(LocalDensity.current) { 26.dp.toSp() })
            Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Preview(name = "Expense")
@Preview(name = "Expense dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Expense large font", widthDp = 320, fontScale = 2f)
@Composable
private fun OperationTypeExpensePreview() {
    FillinTheme { OperationTypeTabs(OperationType.Expense, true, {}) }
}

@Preview(name = "Income")
@Preview(name = "Income dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Income large font", widthDp = 320, fontScale = 2f)
@Composable
private fun OperationTypeIncomePreview() {
    FillinTheme { OperationTypeTabs(OperationType.Income, true, {}) }
}

@Preview(name = "Disabled expense")
@Preview(name = "Disabled expense dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationTypeDisabledExpensePreview() {
    FillinTheme { OperationTypeTabs(OperationType.Expense, false, {}) }
}

@Preview(name = "Disabled income")
@Preview(name = "Disabled income dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationTypeDisabledIncomePreview() {
    FillinTheme { OperationTypeTabs(OperationType.Income, false, {}) }
}

@Preview(name = "Category")
@Preview(name = "Category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryPreview() {
    FillinTheme { OperationCategoryCard("Продукты", "🍔", true, {}) }
}

@Preview(name = "Disabled category")
@Preview(name = "Disabled category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryDisabledPreview() {
    FillinTheme { OperationCategoryCard("Продукты", "🍔", false, {}) }
}

@Preview(name = "Long category", fontScale = 2f)
@Preview(name = "Long category dark", fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryLongPreview() {
    FillinTheme { OperationCategoryCard("Обслуживание автомобиля", "🚕", true, {}) }
}

@Preview(name = "Category row", widthDp = 320)
@Preview(name = "Category row dark", widthDp = 320, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryRowPreview() {
    FillinTheme { OperationCategoryRow(previewOperationCategories(), true, {}) }
}

@Preview(name = "Single category")
@Preview(name = "Single category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryRowSinglePreview() {
    FillinTheme { OperationCategoryRow(previewOperationCategories().take(1), true, {}) }
}

@Preview(name = "Disabled row")
@Preview(name = "Disabled row dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCategoryRowDisabledPreview() {
    FillinTheme { OperationCategoryRow(previewOperationCategories(), false, {}) }
}

internal fun previewOperationCategories() = listOf(
    Category("food", "Продукты", CategoryKind.Expense, CategoryIcon("FOOD", "🍔")),
    Category("transport", "Транспорт", CategoryKind.Expense, CategoryIcon("TRANSPORT", "🚕")),
    Category("home", "Дом", CategoryKind.Expense, CategoryIcon("HOME", "🏠")),
    Category("health", "Здоровье", CategoryKind.Expense, CategoryIcon("HEALTH", "💊")),
    Category("salary", "Зарплата", CategoryKind.Income, CategoryIcon("INCOME", "💰")),
)

@Preview(name = "Editor", widthDp = 393, heightDp = 700)
@Preview(name = "Editor dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateEditorPreview() {
    FillinTheme {
        OperationCreateEditor(OperationCreateEditorUiState(occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {})
    }
}

@Preview(name = "Income editor", widthDp = 393, heightDp = 700)
@Preview(name = "Income editor dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateIncomeEditorPreview() {
    FillinTheme {
        OperationCreateEditor(
            OperationCreateEditorUiState(type = OperationType.Income, input = "Зарплата 120000", occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {},
        )
    }
}

@Preview(name = "Input error", widthDp = 393, heightDp = 700)
@Preview(name = "Input error dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateErrorEditorPreview() {
    FillinTheme {
        OperationCreateEditor(
            OperationCreateEditorUiState(input = "Продукты", inputError = OperationInputError.InvalidAmount, occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {},
        )
    }
}

@Preview(name = "Saving", widthDp = 393, heightDp = 700)
@Preview(name = "Saving dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateSavingEditorPreview() {
    FillinTheme {
        OperationCreateEditor(
            OperationCreateEditorUiState(input = "Продукты 1200", isSaving = true, occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {},
        )
    }
}

@Preview(name = "No categories", widthDp = 320, heightDp = 600)
@Preview(name = "No categories dark", widthDp = 320, heightDp = 600, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateEmptyEditorPreview() {
    FillinTheme {
        OperationCreateEditor(OperationCreateEditorUiState(occurredOn = LocalDate.of(2026, 10, 24)), OfflineHistoryContext(), {})
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
