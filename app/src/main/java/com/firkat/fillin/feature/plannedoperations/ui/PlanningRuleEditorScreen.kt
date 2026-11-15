package com.firkat.fillin.feature.plannedoperations.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.feature.plannedoperations.ui.bottomsheets.PlanningRuleEditorBottomSheetHost
import com.firkat.fillin.feature.sync.ui.BudgetSyncViewModel
import com.firkat.fillin.ui.component.OperationTypeTabs
import com.firkat.fillin.ui.component.SaldomiTextField
import com.firkat.fillin.ui.theme.FillinTheme
import java.time.format.DateTimeFormatter
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PlanningRuleEditorRoute(
    type: PlannedOperationType,
    ruleId: String?,
    requestId: String,
    onClose: () -> Unit,
    model: PlanningRuleEditorViewModel =
        koinViewModel(key = "rule-editor-$requestId", parameters = { parametersOf(type, ruleId) }),
    sync: BudgetSyncViewModel = koinViewModel()
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context by sync.context.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    BackHandler { model.back() }
    PlanningRuleEditorScreen(
        state, context.canWrite, ruleId != null, model::change, model::type, model::window, model::category,
        model::back, model::close, model::save, model::delete, model::categories, model::load
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanningRuleEditorScreen(
    state: RuleEditorState,
    canWrite: Boolean,
    isEditing: Boolean,
    onChange: ((RuleDraft) -> RuleDraft) -> Unit,
    onType: (PlannedOperationType) -> Unit,
    onWindow: (RuleWindow?) -> Unit,
    onCategory: (String) -> Unit,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRetryCategories: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = canWrite && !state.saving && !state.loading
    val draft = state.draft
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("planning-rule-editor"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditing) {
                                R.string.rule_edit_title
                            } else {
                                R.string.rule_create_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("planned-editor-close"),
                        enabled = !state.saving
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.navigation_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isEditing) {
                    OperationTypeTabs(
                        type = when (draft.type) {
                            PlannedOperationType.Expense -> OperationType.Expense
                            PlannedOperationType.Income -> OperationType.Income
                        },
                        enabled = enabled,
                        onTypeClick = { type ->
                            onType(
                                when (type) {
                                    OperationType.Expense -> PlannedOperationType.Expense
                                    OperationType.Income -> PlannedOperationType.Income
                                }
                            )
                        }
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (draft.type == PlannedOperationType.Expense) {
                                R.string.planned_operations_expense
                            } else {
                                R.string.planned_operations_income
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                if (state.loading) CircularProgressIndicator()
                SaldomiTextField(
                    draft.name,
                    { value ->
                        onChange { it.copy(name = value) }
                    },
                    placeholder = stringResource(R.string.rule_name),
                    enabled = enabled,
                    isError =
                        draft.name.length > 160,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule-name")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.rule_category),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onWindow(RuleWindow.Category)
                        },
                        enabled = enabled
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.categories.firstOrNull { it.id == draft.categoryId }?.name
                                    ?: state.original?.takeIf { it.categoryId == draft.categoryId }?.categoryName
                                    ?: stringResource(R.string.rule_select),
                                modifier = Modifier.weight(1f)
                            )
                            Text("›")
                        }
                    }
                }
                SaldomiTextField(
                    draft.amount,
                    { value ->
                        onChange { it.copy(amount = value) }
                    },
                    placeholder = stringResource(
                        R.string.rule_amount,
                        com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency.current.symbol
                    ),
                    enabled = enabled,
                    isError =
                        draft.amount.isNotEmpty() && state.amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("planned-editor-amount")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.rule_frequency),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            // TODO select frequency bottom sheet
                        },
                        enabled = enabled
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(draft.frequency.editorTitleResource),
                                modifier = Modifier.weight(1f)
                            )
                            Text("›")
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.rule_date),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = { onWindow(RuleWindow.Date) }
                            )
                    ) {
                        Text(
                            draft.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
                            Modifier.padding(16.dp)
                        )
                    }
                }
                if (state.original != null &&
                    draft.type == PlannedOperationType.Income
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = stringResource(R.string.income_active_label),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = draft.active,
                            onCheckedChange = { value -> onChange { it.copy(active = value) } },
                            enabled = enabled
                        )
                    }
                }
                if (state.error) {
                    Text(stringResource(R.string.rule_error), color = MaterialTheme.colorScheme.error)
                    if (state.original == null &&
                        !state.dirty
                    ) {
                        TextButton(
                            onClick = onRetry
                        ) {
                            Text(stringResource(R.string.planned_operations_retry))
                        }
                    }
                }
                if (state.original !=
                    null
                ) {
                    TextButton(
                        onClick = {
                            onWindow(RuleWindow.Delete)
                        },
                        enabled = enabled
                    ) {
                        Text(
                            text = stringResource(R.string.rule_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSave,
                    enabled = canWrite && state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("planned-editor-save")
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(stringResource(R.string.rule_save))
                    }
                }
                if (draft.frequency ==
                    PlanningFrequency.Once
                ) {
                    Text(
                        text = stringResource(R.string.rule_once_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    PlanningRuleEditorBottomSheetHost(
        window = state.window,
        type = draft.type,
        categoryId = draft.categoryId,
        categories = state.categories,
        categoriesLoading = state.categoriesLoading,
        categoriesError = state.categoriesError,
        date = draft.date,
        accountingStart = state.accountingStart,
        originalDate = state.original?.let { state.initial.date },
        canSelectDate = enabled,
        canDiscard = !state.saving,
        canDelete = !state.saving && canWrite,
        onCategorySelected = onCategory,
        onRetryCategories = onRetryCategories,
        onDateSelected = { date -> onChange { it.copy(date = date) } },
        onDiscard = onDiscard,
        onDelete = onDelete,
        onDismiss = { onWindow(null) }
    )
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Preview(widthDp = 320, heightDp = 600, fontScale = 2f)
@Preview(widthDp = 840)
@Composable
private fun RuleEditorPreview() {
    FillinTheme {
        PlanningRuleEditorScreen(RuleEditorState(loading = false), true, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Composable
private fun EditorPreviewState(
    state: RuleEditorState
) {
    FillinTheme { PlanningRuleEditorScreen(state, true, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeRulePreview() {
    EditorPreviewState(
        RuleEditorState(
            draft = RuleDraft(
                type = PlannedOperationType.Income,
                name = "Зарплата",
                amount = "250000",
                frequency = PlanningFrequency.Monthly
            ),
            loading = false
        )
    )
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RuleSavingPreview() {
    EditorPreviewState(RuleEditorState(loading = false, saving = true))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RuleErrorPreview() {
    EditorPreviewState(RuleEditorState(loading = false, error = true))
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RuleLoadingPreview() {
    EditorPreviewState(RuleEditorState())
}

private val PlanningFrequency.editorTitleResource: Int get() = when (this) {
    PlanningFrequency.Once -> R.string.income_frequency_once
    PlanningFrequency.Weekly -> R.string.income_frequency_weekly
    PlanningFrequency.Biweekly -> R.string.income_frequency_biweekly
    PlanningFrequency.Monthly -> R.string.income_frequency_monthly
}
