package com.firkat.fillin.feature.budgetmanagement.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetmanagement.domain.validBudgetName
import com.firkat.fillin.feature.budgetmanagement.ui.bottomsheets.BudgetCurrencyConfirmation
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.budgetprofile.ui.bottomsheets.BudgetCurrencyPicker
import com.firkat.fillin.feature.budgetprofile.ui.currencyLabel
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import com.firkat.fillin.feature.profile.ui.budgetDisplayName
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiTextField
import com.firkat.fillin.ui.component.SettingsScaffold
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun BudgetEditRoute(
    budgetId: String,
    onBack: () -> Unit,
    repository: ProfileBudgetsRepository = koinInject(),
    management: BudgetManagementViewModel = koinViewModel()
) {
    val budgets by remember(repository) { repository.observe() }.collectAsStateWithLifecycle(initialValue = null)
    val draft by management.state.collectAsStateWithLifecycle()
    val catalog = budgets
    val budgetIndex = catalog?.indexOfFirst { it.id == budgetId && it.canManage } ?: -1
    val budget = catalog?.getOrNull(budgetIndex)
    val name = budget?.let { catalog.budgetDisplayName(budgetIndex) }
    val current = draft
    LaunchedEffect(catalog != null, budget, name, current == null) {
        if (catalog != null) {
            if (budget == null || (current == null && management.editingStarted)) {
                onBack()
            } else if (current == null && name != null) {
                management.startEditing(budget.id, name, budget.currency)
            }
        }
    }

    BudgetEditScreen(
        state = current?.takeIf { budget != null },
        onNameChange = management::change,
        onCurrencyClick = management::openCurrencyPicker,
        onSave = management::confirm,
        onBack = onBack
    )
    if (current != null && budget != null) {
        if (current.pickerOpen) {
            BudgetCurrencyPicker(current.currency, management::selectCurrency, management::closeCurrencyPicker)
        } else if (current.confirmCurrency) {
            BudgetCurrencyConfirmation(
                current.originalCurrency,
                current.currency,
                management::confirmCurrencyChange,
                management::dismissCurrencyConfirmation
            )
        }
    }
}

@Composable
internal fun BudgetEditScreen(
    state: BudgetManagementState?,
    onNameChange: (String) -> Unit,
    onCurrencyClick: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val busy = state?.busy == true
    BackHandler(enabled = busy) {}
    val focus = LocalFocusManager.current
    SettingsScaffold(
        title = stringResource(R.string.budget_manage_rename),
        onBack = onBack,
        modifier = Modifier.testTag("budget_edit_screen"),
        backEnabled = !busy
    ) { contentPadding ->
        if (state == null) {
            Box(
                Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .imePadding()
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    val nameLabel = stringResource(R.string.cloud_budget_name)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            nameLabel,
                            modifier = Modifier.clearAndSetSemantics {},
                            style = MaterialTheme.typography.titleSmall
                        )
                        SaldomiTextField(
                            value = state.value,
                            onValueChange = onNameChange,
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("budget_management_input")
                                .semantics { contentDescription = nameLabel }
                        )
                    }
                    BudgetEditCurrencyBlock(
                        currency = state.currency,
                        enabled = !busy,
                        onClick = {
                            focus.clearFocus()
                            onCurrencyClick()
                        },
                        modifier = Modifier.testTag("budget_management_currency")
                    )
                    if (state.error) {
                        Text(
                            stringResource(R.string.budget_manage_error),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                SaldomiButton(
                    onClick = {
                        focus.clearFocus()
                        onSave()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .testTag("budget_management_confirm"),
                    large = true,
                    isLoading = busy,
                    enabled = !busy && validBudgetName(state.value)
                ) {
                    Text(stringResource(R.string.budget_manage_save))
                }
            }
        }
    }
}

@Composable
private fun BudgetEditCurrencyBlock(
    currency: BudgetCurrency,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(stringResource(R.string.setup_currency)) },
        supportingContent = { Text(currencyLabel(currency)) },
        leadingContent = { Icon(FillinIcons.Currency, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = colors.secondaryContainer,
            headlineColor = colors.onSecondaryContainer,
            supportingColor = colors.onSecondaryContainer,
            leadingIconColor = colors.onSecondaryContainer,
            trailingIconColor = colors.onSecondaryContainer
        )
    )
}

@Preview(name = "Edit budget")
@Preview(name = "Edit budget dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Edit budget compact", widthDp = 320, fontScale = 2f)
@Composable
private fun BudgetEditPreview() = EditPreview(
    BudgetManagementState("1", "Семейный бюджет", false, "Семейный бюджет на следующий год")
)

@Preview(name = "Saving")
@Preview(name = "Saving dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BudgetEditSavingPreview() = EditPreview(
    BudgetManagementState("1", "Семейный", false, "Дом", busy = true)
)

@Preview(name = "Save error")
@Preview(name = "Save error dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BudgetEditErrorPreview() = EditPreview(
    BudgetManagementState("1", "Семейный", false, "Дом", error = true, currency = BudgetCurrency.EUR)
)

@Composable
private fun EditPreview(state: BudgetManagementState) {
    FillinTheme { BudgetEditScreen(state, {}, {}, {}, {}) }
}
