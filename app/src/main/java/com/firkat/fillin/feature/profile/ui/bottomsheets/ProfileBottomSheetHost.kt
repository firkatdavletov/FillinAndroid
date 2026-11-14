package com.firkat.fillin.feature.profile.ui.bottomsheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.firkat.fillin.feature.appaccess.ui.bottomsheets.AboutAppBottomSheet
import com.firkat.fillin.feature.budgetmanagement.ui.BudgetManagementState
import com.firkat.fillin.feature.budgetmanagement.ui.bottomsheets.BudgetCurrencyConfirmation
import com.firkat.fillin.feature.budgetmanagement.ui.bottomsheets.BudgetManagementSheet
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.budgetprofile.ui.bottomsheets.BudgetCurrencyPicker
import com.firkat.fillin.feature.profile.ui.AccountConfirmation
import com.firkat.fillin.feature.profile.ui.AccountConfirmationKind

@Composable
internal fun ProfileBottomSheetHost(
    installationId: String?,
    accessChecking: Boolean,
    accessRefreshResult: Boolean?,
    fullAccess: Boolean,
    management: BudgetManagementState?,
    userId: String?,
    confirmation: AccountConfirmation?,
    onRefreshAccess: () -> Unit,
    onCloseAbout: () -> Unit,
    onBudgetNameChanged: (String) -> Unit,
    onConfirmBudget: () -> Unit,
    onCloseBudget: () -> Unit,
    onOpenCurrencyPicker: () -> Unit,
    onCurrencySelected: (BudgetCurrency) -> Unit,
    onCloseCurrencyPicker: () -> Unit,
    onConfirmCurrencyChange: () -> Unit,
    onDismissCurrencyChange: () -> Unit,
    onSignOut: () -> Unit,
    onAccountConfirmationChanged: (String) -> Unit,
    onDeleteAccount: () -> Unit,
    onDismissAccountConfirmation: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (installationId != null) {
        AboutAppBottomSheet(
            id = installationId,
            checking = accessChecking,
            refreshResult = accessRefreshResult,
            onRefresh = onRefreshAccess,
            onClose = onCloseAbout,
            modifier = modifier
        )
    }
    management?.takeIf { fullAccess || !it.family }?.let { budget ->
        if (budget.pickerOpen) {
            BudgetCurrencyPicker(budget.currency, onCurrencySelected, onCloseCurrencyPicker, modifier)
        } else {
            BudgetManagementSheet(
                state = budget,
                onChange = onBudgetNameChanged,
                onConfirm = onConfirmBudget,
                onDismiss = onCloseBudget,
                modifier = modifier,
                onCurrencyClick = onOpenCurrencyPicker
            )
        }
        if (budget.confirmCurrency) {
            BudgetCurrencyConfirmation(
                budget.originalCurrency,
                budget.currency,
                onConfirmCurrencyChange,
                onDismissCurrencyChange,
                modifier
            )
        }
    }
    val current = confirmation?.takeIf { it.uid == userId }
    when (current?.kind) {
        AccountConfirmationKind.SignOut -> SignOutAccountBottomSheet(
            onConfirm = onSignOut,
            onDismiss = onDismissAccountConfirmation,
            modifier = modifier
        )
        AccountConfirmationKind.Delete -> DeleteAccountBottomSheet(
            value = current.value,
            onChange = onAccountConfirmationChanged,
            onConfirm = onDeleteAccount,
            onDismiss = onDismissAccountConfirmation,
            modifier = modifier
        )
        null -> Unit
    }
}
