package com.firkat.fillin.feature.budgetsetup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionSection
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun setupError(error: Exception): String =
    when ((error as? BudgetConnectionFailure)?.code) {
        "not_local_budget" -> "Этот бюджет уже связан с облаком. Подключите его из списка бюджетов аккаунта."
        "creation_pending" -> "Создание предыдущего бюджета ещё не завершено. Продолжите его создание."
        "creation_conflict" -> "Сервер отклонил повтор создания. Запрос сохранён; обратитесь к разработчику."
        "invalid_creation" -> "Проверьте название, начальный баланс и дату бюджета."
        "budget_not_found" -> "Бюджет не найден. Обновите список бюджетов аккаунта."
        "budget_already_exists" -> "Не удалось подключить бюджет. Локальные данные сохранены; выберите бюджет аккаунта."
        "unauthorized", "access_denied" -> "Нет доступа к облачному бюджету. Проверьте Google-аккаунт или войдите снова."
        "sign_in_required" -> "Войдите через Google и выберите доступный бюджет."
        "account_changed" -> "Аккаунт изменился. Выберите бюджет текущего аккаунта."
        "server_not_configured" -> "Серверное подключение не настроено в этой сборке."
        "unsupported_legacy_queue" -> "Этот бюджет использует прежний формат обмена. Данные сохранены; подключение остановлено."
        "missing_api_token" -> "Войдите через Google и подключите бюджет к аккаунту. Локальные данные сохранены."
        "scope_changed" -> "Контекст бюджета изменился. Обмен остановлен; локальные данные сохранены."
        else -> if (error is IllegalArgumentException || error is java.time.DateTimeException) "Проверьте баланс и дату начала учёта." else "Не удалось подключиться к серверу. Проверьте интернет и повторите попытку."
    }

@Composable
fun BudgetSetupRoute(mode: BudgetSetupMode, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag("budget_setup_screen"), color = MaterialTheme.colorScheme.background) {
        when (mode) {
            BudgetSetupMode.Local -> BudgetSetupScreen(onBack = onBack)
            BudgetSetupMode.Cloud -> CloudBudgetSetupScreen(onBack = onBack)
        }
    }
}

@Composable
fun BudgetSetupScreen(
    viewModel: BudgetSetupViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    var balance by rememberSaveable { mutableStateOf("0") }
    var date by rememberSaveable { mutableStateOf(viewModel.defaultStartDate.toString()) }
    BackHandler { if (!viewModel.busy) onBack() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LocalBudgetSetupContent(viewModel, balance, { balance = it }, date, { date = it }, onBack)
    }
}

@Composable
private fun LocalBudgetSetupContent(
    viewModel: BudgetSetupViewModel,
    balance: String,
    onBalanceChange: (String) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Fillin", style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.budget_setup_local_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(balance, onBalanceChange, label = { Text(stringResource(R.string.cloud_budget_opening_balance)) }, enabled = !viewModel.busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, onDateChange, label = { Text(stringResource(R.string.cloud_budget_start_date)) }, enabled = !viewModel.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.budget_setup_local_hint))
        Button(onClick = { viewModel.create(balance, date) }, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.budget_setup_create)) }
        TextButton(onClick = onBack, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.navigation_back)) }
        if (viewModel.busy) {
            CircularProgressIndicator()
            Text(stringResource(R.string.budget_setup_loading))
        }
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

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
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Fillin", style = MaterialTheme.typography.headlineLarge)
        if (joiningFamily) {
            com.firkat.fillin.feature.budgetsharing.ui.FamilyBudgetJoinSection()
            TextButton(onClick = { joiningFamily = false }, modifier = Modifier.fillMaxWidth()) { Text("К списку бюджетов") }
        } else {
            BudgetConnectionSection(viewModel, showCreateCancel = false)
            TextButton(onClick = { joiningFamily = true }, enabled = !state.busy && state.pending == null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_join_title)) }
        }
        TextButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.navigation_back)) }
    }
}
