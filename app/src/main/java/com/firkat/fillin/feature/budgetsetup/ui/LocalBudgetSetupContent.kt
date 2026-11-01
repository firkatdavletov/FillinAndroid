package com.firkat.fillin.feature.budgetsetup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.component.SaldomiTextField

@Composable
internal fun LocalBudgetSetupContent(
    viewModel: BudgetSetupViewModel,
    balance: String,
    onBalanceChange: (String) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.budget_setup_local_title), style = MaterialTheme.typography.headlineSmall)
        SaldomiTextField(
            value = balance,
            onValueChange = onBalanceChange,
            enabled = !viewModel.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        SaldomiTextField(
            value = date,
            onValueChange = onDateChange,
            enabled = !viewModel.busy,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.create(balance, date) },
            enabled = !viewModel.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.budget_setup_create)
            )
        }
        TextButton(
            onClick = onBack,
            enabled = !viewModel.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.navigation_back))
        }
        if (viewModel.busy) {
            CircularProgressIndicator()
            Text(stringResource(R.string.budget_setup_loading))
        }
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
