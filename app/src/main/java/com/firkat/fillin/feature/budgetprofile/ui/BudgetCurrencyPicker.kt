@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firkat.fillin.feature.budgetprofile.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.ui.component.SaldomiBottomSheet
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun BudgetCurrencyRow(
    currency: BudgetCurrency,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.setup_currency)) },
        supportingContent = { Text(currencyLabel(currency)) },
        leadingContent = { Icon(FillinIcons.Currency, null) },
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
    )
}

@Composable
internal fun BudgetCurrencyPicker(
    selected: BudgetCurrency,
    onSelect: (BudgetCurrency) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldomiBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        BudgetCurrencyOptions(selected, onSelect)
    }
}

@Composable
private fun BudgetCurrencyOptions(
    selected: BudgetCurrency,
    onSelect: (BudgetCurrency) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .selectableGroup()
    ) {
        Text(stringResource(R.string.budget_currency_picker_title), Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge)
        BudgetCurrency.entries.forEach { currency ->
            ListItem(
                headlineContent = {
                    Text(currencyLabel(currency))
                },
                trailingContent = {
                    RadioButton(selected = currency == selected, onClick = null)
                },
                modifier = Modifier
                    .selectable(
                        selected = currency == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(currency) }
                    ),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    }
}

@Composable
private fun currencyLabel(currency: BudgetCurrency): String = stringResource(
    when (currency) {
        BudgetCurrency.KZT -> R.string.budget_currency_kzt
        BudgetCurrency.RUB -> R.string.budget_currency_rub
        BudgetCurrency.USD -> R.string.budget_currency_usd
        BudgetCurrency.EUR -> R.string.budget_currency_eur
    },
)

@Preview(name = "RUB")
@Preview(name = "RUB dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Large text", widthDp = 320, fontScale = 2f)
@Composable private fun RubPreview() { FillinTheme { BudgetCurrencyOptions(BudgetCurrency.RUB, {}) } }
@Preview(name = "KZT")
@Preview(name = "KZT dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun KztPreview() { FillinTheme { BudgetCurrencyOptions(BudgetCurrency.KZT, {}) } }
@Preview(name = "USD")
@Preview(name = "USD dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun UsdPreview() { FillinTheme { BudgetCurrencyOptions(BudgetCurrency.USD, {}) } }
@Preview(name = "EUR")
@Preview(name = "EUR dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EurPreview() { FillinTheme { BudgetCurrencyOptions(BudgetCurrency.EUR, {}) } }

@Preview(name = "Currency field")
@Preview(name = "Currency field dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun CurrencyFieldPreview() { FillinTheme { BudgetCurrencyRow(BudgetCurrency.RUB, true, {}) } }
@Preview(name = "Currency field disabled")
@Preview(name = "Currency field disabled dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun CurrencyFieldDisabledPreview() { FillinTheme { BudgetCurrencyRow(BudgetCurrency.KZT, false, {}) } }
