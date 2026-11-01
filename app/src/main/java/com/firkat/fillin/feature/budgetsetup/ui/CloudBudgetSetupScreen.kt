package com.firkat.fillin.feature.budgetsetup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionSection
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetsharing.ui.FamilyBudgetJoinSection
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CloudBudgetSetupScreen(
    viewModel: BudgetConnectionViewModel = koinViewModel(
        key = "onboarding-budget-connection",
        parameters = { parametersOf(true) },
    ),
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var joiningFamily by rememberSaveable { mutableStateOf(false) }
    // Budget-scoped ViewModels are recreated with the composition on Activity recreation.
    // Restore only an unsent draft; the persisted pending request always takes precedence.
    LaunchedEffect(viewModel) {
        var restored = false
        viewModel.state.collect { value ->
            if (value.loaded && !value.busy) {
                if (!restored) {
                    restored = true
                    if (value.pending == null && value.draft != null && draft != null) {
                        draft?.let { viewModel.editDraft(BudgetCreationDraft(it[0], it[1], it[2])) }
                        return@collect
                    }
                }
                draft = value.draft?.let { listOf(it.name, it.openingBalance, it.accountingStartedOn) }
            }
        }
    }
    BackHandler { if (!state.busy) onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Fillin",
            style = MaterialTheme.typography.headlineLarge
        )
        if (joiningFamily) {
            FamilyBudgetJoinSection()
            TextButton(
                onClick = { joiningFamily = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("К списку бюджетов")
            }
        } else {
            BudgetConnectionSection(
                viewModel = viewModel,
                showCreateCancel = false
            )
            TextButton(
                onClick = { joiningFamily = true },
                enabled = !state.busy && state.pending == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.family_join_title))
            }
        }
        TextButton(
            onClick = onBack,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.navigation_back)
            )
        }
    }
}