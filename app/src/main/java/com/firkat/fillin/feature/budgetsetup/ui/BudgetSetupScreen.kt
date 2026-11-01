package com.firkat.fillin.feature.budgetsetup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionSection
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import com.firkat.fillin.ui.component.SaldomiTextField
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun BudgetSetupScreen(
    viewModel: BudgetSetupViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    var balance by rememberSaveable { mutableStateOf("0") }
    var date by rememberSaveable { mutableStateOf(viewModel.defaultStartDate.toString()) }
    BackHandler { if (!viewModel.busy) onBack() }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Image(
            painter = painterResource(
                R.drawable.saldomi_auth_background
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        LocalBudgetSetupContent(
            viewModel = viewModel,
            balance = balance,
            onBalanceChange = { balance = it },
            date = date,
            onDateChange = { date = it },
            onBack = onBack
        )
    }
}