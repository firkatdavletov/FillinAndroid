package com.firkat.fillin.feature.plannedoperations.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.ui.CategoryItem
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.sync.ui.BudgetSyncViewModel
import com.firkat.fillin.ui.component.*
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun PlanningRuleEditorRoute(type: PlannedOperationType, ruleId: String?, requestId: String, onClose: () -> Unit,
    model: PlanningRuleEditorViewModel = koinViewModel(key = "rule-editor-$requestId", parameters = { parametersOf(type, ruleId) }),
    sync: BudgetSyncViewModel = koinViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context by sync.context.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    BackHandler { model.back() }
    PlanningRuleEditorScreen(state, context.canWrite, model::change, model::type, model::window, model::category,
        model::back, model::close, model::save, model::delete, model::categories, model::load)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanningRuleEditorScreen(state: RuleEditorState, canWrite: Boolean,
    onChange: ((RuleDraft) -> RuleDraft) -> Unit, onType: (PlannedOperationType) -> Unit,
    onWindow: (RuleWindow?) -> Unit, onCategory: (String) -> Unit, onBack: () -> Unit, onDiscard: () -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, onRetryCategories: () -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier) {
    val enabled = canWrite && !state.saving && !state.loading
    val draft = state.draft
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Scaffold(modifier.fillMaxSize().testTag("planning-rule-editor"), topBar = {
        TopAppBar(title = { Text(stringResource(if (state.original == null) R.string.rule_create_title else R.string.rule_edit_title)) },
            navigationIcon = { IconButton(onBack, modifier = Modifier.testTag("planned-editor-close"), enabled = !state.saving) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigation_back)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryTabRow(selectedTabIndex = if (draft.type == PlannedOperationType.Expense) 0 else 1) {
                    PlannedOperationType.entries.forEach { type -> Tab(selected = type == draft.type,
                        enabled = enabled && state.original == null, onClick = { onType(type) },
                        text = { Text(stringResource(if (type == PlannedOperationType.Expense) R.string.planned_operations_expense else R.string.planned_operations_income)) }) }
                }
                if (state.loading) CircularProgressIndicator()
                SaldomiTextField(draft.name, { value -> onChange { it.copy(name = value) } }, label = stringResource(R.string.rule_name), enabled = enabled, isError = draft.name.length > 160, modifier = Modifier.fillMaxWidth().testTag("rule-name"))
                RuleValue(stringResource(R.string.rule_category), state.categories.firstOrNull { it.id == draft.categoryId }?.name ?: state.original?.takeIf { it.categoryId == draft.categoryId }?.categoryName ?: stringResource(R.string.rule_select), enabled, { onWindow(RuleWindow.Category) })
                SaldomiTextField(draft.amount, { value -> onChange { it.copy(amount = value) } }, label = stringResource(R.string.rule_amount), enabled = enabled, isError = draft.amount.isNotEmpty() && state.amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("planned-editor-amount"))
                Box {
                    RuleValue(stringResource(R.string.rule_frequency), stringResource(draft.frequency.editorTitleResource), enabled, { onWindow(RuleWindow.Frequency) })
                    DropdownMenu(state.window == RuleWindow.Frequency, { onWindow(null) }) {
                        PlanningFrequency.entries.filter { draft.type == PlannedOperationType.Income || it != PlanningFrequency.Biweekly }.forEach { frequency ->
                            DropdownMenuItem(text = { Text(stringResource(frequency.editorTitleResource)) }, onClick = { onChange { it.copy(frequency = frequency) }; onWindow(null) })
                        }
                    }
                }
                RuleValue(stringResource(R.string.rule_date), draft.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)), enabled, { onWindow(RuleWindow.Date) })
                if (state.original != null && draft.type == PlannedOperationType.Income) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.income_active_label), Modifier.weight(1f))
                    Switch(draft.active, { value -> onChange { it.copy(active = value) } }, enabled = enabled)
                }
                if (state.error) {
                    Text(stringResource(R.string.rule_error), color = MaterialTheme.colorScheme.error)
                    if (state.original == null && !state.dirty) TextButton(onRetry) { Text(stringResource(R.string.planned_operations_retry)) }
                }
                if (state.original != null) TextButton(onClick = { onWindow(RuleWindow.Delete) }, enabled = enabled) { Text(stringResource(R.string.rule_delete), color = MaterialTheme.colorScheme.error) }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onSave, enabled = canWrite && state.canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("planned-editor-save")) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(24.dp)) else Text(stringResource(R.string.rule_save))
                }
                if (draft.frequency == PlanningFrequency.Once) Text(stringResource(R.string.rule_once_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (state.window == RuleWindow.Category) SaldomiBottomSheet(onDismissRequest = { onWindow(null) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.rule_category), style = MaterialTheme.typography.titleLarge)
            when {
                state.categoriesLoading -> CircularProgressIndicator()
                state.categoriesError -> { Text(stringResource(R.string.rule_error)); TextButton(onRetryCategories) { Text(stringResource(R.string.planned_operations_retry)) } }
                else -> {
                    val categories = state.categories.filter { it.kind == if (draft.type == PlannedOperationType.Expense) CategoryKind.Expense else CategoryKind.Income }
                    if (categories.isEmpty()) Text(stringResource(R.string.rule_no_categories))
                    categories.forEach { category ->
                        Surface(color = if (category.id == draft.categoryId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.medium) {
                            CategoryItem(category, { onCategory(category.id) }, Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics { selected = category.id == draft.categoryId })
                        }
                    }
                }
            }
        }
    }
    if (state.window == RuleWindow.Date) {
        val minimumDate = maxOf(state.accountingStart, LocalDate.now(ZoneId.of("Asia/Almaty")))
        val picker = rememberDatePickerState(initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return date >= minimumDate || state.original != null && date == state.initial.date
                }
            })
        DatePickerDialog(onDismissRequest = { onWindow(null) }, confirmButton = {
            TextButton(enabled = enabled && picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { millis -> onChange { it.copy(date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) } }; onWindow(null)
            }) { Text(stringResource(R.string.rule_select)) }
        }, dismissButton = { TextButton({ onWindow(null) }) { Text(stringResource(R.string.rule_cancel)) } }) { DatePicker(picker) }
    }
    if (state.window == RuleWindow.Discard || state.window == RuleWindow.Delete) AlertDialog(onDismissRequest = { onWindow(null) },
        title = { Text(stringResource(if (state.window == RuleWindow.Delete) R.string.rule_delete_question else R.string.rule_discard_question)) },
        confirmButton = { TextButton(if (state.window == RuleWindow.Delete) onDelete else onDiscard, enabled = !state.saving && (state.window != RuleWindow.Delete || canWrite)) { Text(stringResource(R.string.rule_confirm)) } },
        dismissButton = { TextButton({ onWindow(null) }) { Text(stringResource(R.string.rule_cancel)) } })
}

@Composable
private fun RuleValue(label: String, value: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun RuleValuePreview() { FillinTheme { RuleValue("Категория", "Продукты", true, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun RuleValueDisabledPreview() { FillinTheme { RuleValue("Категория", "Выберите", false, {}) } }
@Preview(widthDp = 320, fontScale = 2f) @Preview(widthDp = 840) @Composable private fun RuleValueLongPreview() { FillinTheme { RuleValue("Дата первого выполнения", "30 сентября 2026", true, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Preview(widthDp = 320, heightDp = 600, fontScale = 2f) @Preview(widthDp = 840)
@Composable private fun RuleEditorPreview() { FillinTheme { PlanningRuleEditorScreen(RuleEditorState(loading = false), true, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }

@Composable
private fun EditorPreviewState(state: RuleEditorState) {
    FillinTheme { PlanningRuleEditorScreen(state, true, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun IncomeRulePreview() {
    EditorPreviewState(RuleEditorState(draft = RuleDraft(type = PlannedOperationType.Income, name = "Зарплата", amount = "250000", frequency = PlanningFrequency.Monthly), loading = false))
}
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun RuleSavingPreview() { EditorPreviewState(RuleEditorState(loading = false, saving = true)) }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun RuleErrorPreview() { EditorPreviewState(RuleEditorState(loading = false, error = true)) }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun RuleLoadingPreview() { EditorPreviewState(RuleEditorState()) }

private val PlanningFrequency.editorTitleResource: Int get() = when (this) {
    PlanningFrequency.Once -> R.string.income_frequency_once
    PlanningFrequency.Weekly -> R.string.income_frequency_weekly
    PlanningFrequency.Biweekly -> R.string.income_frequency_biweekly
    PlanningFrequency.Monthly -> R.string.income_frequency_monthly
}
