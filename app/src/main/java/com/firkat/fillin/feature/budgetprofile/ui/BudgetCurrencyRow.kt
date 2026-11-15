@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firkat.fillin.feature.budgetprofile.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun BudgetCurrencyRow(
    currency: BudgetCurrency,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            )
    )
}

@Composable
internal fun currencyLabel(
    currency: BudgetCurrency
): String = stringResource(
    when (currency) {
        BudgetCurrency.KZT -> R.string.budget_currency_kzt
        BudgetCurrency.RUB -> R.string.budget_currency_rub
        BudgetCurrency.USD -> R.string.budget_currency_usd
        BudgetCurrency.EUR -> R.string.budget_currency_eur
    }
)

@Preview(name = "Currency field")
@Preview(name = "Currency field dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CurrencyFieldPreview() {
    FillinTheme { BudgetCurrencyRow(BudgetCurrency.RUB, true, {}) }
}

@Preview(name = "Currency field disabled")
@Preview(name = "Currency field disabled dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CurrencyFieldDisabledPreview() {
    FillinTheme { BudgetCurrencyRow(BudgetCurrency.KZT, false, {}) }
}
