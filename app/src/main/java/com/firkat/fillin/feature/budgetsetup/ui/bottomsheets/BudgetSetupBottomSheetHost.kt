package com.firkat.fillin.feature.budgetsetup.ui.bottomsheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.budgetprofile.ui.bottomsheets.BudgetCurrencyPicker
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupPage

@Composable
internal fun BudgetSetupBottomSheetHost(
    pickerOpen: Boolean,
    editable: Boolean,
    page: SetupPage,
    currency: BudgetCurrency,
    onCurrencySelected: (BudgetCurrency) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pickerOpen && editable && page == SetupPage.Create) {
        BudgetCurrencyPicker(currency, onCurrencySelected, onDismiss, modifier)
    }
}
