package com.firkat.fillin.feature.plannedoperations.ui.bottomsheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpenseRuleEditor
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesEvent
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesViewModel
import com.firkat.fillin.feature.plannedexpenses.ui.bottomsheets.PlannedExpensesBottomSheetHost
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomeEditor
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesEvent
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesViewModel
import com.firkat.fillin.feature.plannedincomes.ui.bottomsheets.PlannedIncomesBottomSheetHost
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.ui.navigation.LocalBottomSheetDismissController
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val EXPENSE_EDITOR_KEY = "planned-expense-editor"
private const val INCOME_EDITOR_KEY = "planned-income-editor"

@Composable
internal fun PlannedOperationBottomSheet(
    type: PlannedOperationType,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    expenseEditor: PlannedExpensesViewModel = koinViewModel(
        key = EXPENSE_EDITOR_KEY,
        parameters = { parametersOf(true) }
    ),
    incomeEditor: PlannedIncomesViewModel = koinViewModel(
        key = INCOME_EDITOR_KEY,
        parameters = { parametersOf(true) }
    )
) {
    val expenseState by expenseEditor.uiState.collectAsStateWithLifecycle()
    val incomeState by incomeEditor.uiState.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val dismissController = LocalBottomSheetDismissController.current
    DisposableEffect(dismissController, type, expenseEditor, incomeEditor) {
        val guard = {
            when (type) {
                PlannedOperationType.Expense -> expenseEditor.uiState.value.editor?.isSaving != true
                PlannedOperationType.Income -> incomeEditor.uiState.value.editor?.isSaving != true
            }
        }
        dismissController.canDismiss = guard
        onDispose {
            if (dismissController.canDismiss === guard) dismissController.canDismiss = { true }
        }
    }
    val requestClose = {
        if (dismissController.canDismiss()) {
            when (type) {
                PlannedOperationType.Expense -> expenseEditor.onEvent(PlannedExpensesEvent.EditorClosed)
                PlannedOperationType.Income -> incomeEditor.onEvent(PlannedIncomesEvent.EditorClosed)
            }
            close()
        }
    }
    BackHandler { requestClose() }
    val hasEditor = when (type) {
        PlannedOperationType.Expense -> expenseState.editor != null
        PlannedOperationType.Income -> incomeState.editor != null
    }
    // Successful saves and deletions clear the editor in its ViewModel.
    LaunchedEffect(hasEditor) { if (!hasEditor) close() }

    val contentModifier = modifier.imePadding().testTag("planned-operation-bottom-sheet")
    when (type) {
        PlannedOperationType.Expense -> expenseState.editor?.let { editor ->
            PlannedExpenseRuleEditor(
                categories = expenseState.expenseCategories,
                isCategoriesLoading = expenseState.isCategoriesLoading,
                editor = editor,
                onEvent = expenseEditor::onEvent,
                modifier = contentModifier,
                onClose = requestClose
            )
            PlannedExpensesBottomSheetHost(
                editorOpen = true,
                showDeleteConfirmation = editor.isDeleteConfirmationVisible,
                onDelete = { expenseEditor.onEvent(PlannedExpensesEvent.DeleteConfirmed) },
                onDismissDelete = { expenseEditor.onEvent(PlannedExpensesEvent.DeleteDismissed) }
            )
        }
        PlannedOperationType.Income -> incomeState.editor?.let { editor ->
            PlannedIncomeEditor(
                categories = incomeState.incomeCategories,
                isCategoriesLoading = incomeState.isCategoriesLoading,
                editor = editor,
                onEvent = incomeEditor::onEvent,
                modifier = contentModifier,
                onClose = requestClose
            )
            PlannedIncomesBottomSheetHost(
                showDeleteConfirmation = editor.isDeleteConfirmationVisible,
                onDelete = { incomeEditor.onEvent(PlannedIncomesEvent.DeleteConfirmed) },
                onDismiss = { incomeEditor.onEvent(PlannedIncomesEvent.DeleteDismissed) }
            )
        }
    }
}
