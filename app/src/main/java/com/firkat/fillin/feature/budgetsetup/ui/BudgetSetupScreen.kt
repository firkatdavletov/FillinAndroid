package com.firkat.fillin.feature.budgetsetup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.budgetsetup.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.ZoneId

class BudgetSetupViewModel(
    private val session: BudgetSetupRepository,
    private val createLocal: CreateLocalBudget = CreateLocalBudget(session),
    private val connectExisting: ConnectExistingBudget = ConnectExistingBudget(session),
) : ViewModel() {
    val defaultStartDate = session.today
    val ready = session.ready.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var creating by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun chooseCreate() {
        creating = true
        error = null
    }

    fun back() {
        if (!busy) {
            creating = false
            error = null
        }
    }

    fun create(
        balance: String,
        date: String,
    ) = perform {
        createLocal(balance.trim().replace(',', '.').toBigDecimal(), LocalDate.parse(date))
    }

    fun connect() =
        perform {
            try {
                connectExisting()
            } catch (e: Exception) {
                creating = false
                throw e
            }
        }

    private fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = setupError(e)
            } finally {
                busy = false
            }
        }
    }
}

fun setupError(error: Exception): String =
    when ((error as? BudgetConnectionFailure)?.code) {
        "budget_not_found" -> "Бюджет по этому токену не найден. Создайте бюджет или повторите подключение."
        "budget_already_exists" -> "Токен уже связан с другим бюджетом. Локальный бюджет сохранён; облачная синхронизация выключена."
        "unauthorized", "access_denied" -> "Нет доступа к облачному бюджету. Проверьте Google-аккаунт или войдите снова."
        "sign_in_required" -> "Войдите через Google и выберите доступный бюджет."
        "account_changed" -> "Аккаунт изменился. Выберите бюджет текущего аккаунта."
        "server_not_configured" -> "Серверное подключение не настроено в этой сборке."
        "unsupported_legacy_queue" -> "Этот бюджет использует прежний формат обмена. Данные сохранены; подключение остановлено."
        "missing_api_token" -> "Облачное подключение не настроено в этой сборке. Можно создать локальный бюджет."
        "scope_changed" -> "Токен относится к другому бюджету. Обмен остановлен; локальные данные сохранены."
        else -> if (error is IllegalArgumentException) "Проверьте баланс и дату начала учёта." else "Не удалось подключиться к серверу. Проверьте интернет и повторите попытку."
    }

@Composable
fun BudgetSetupGate(content: @Composable () -> Unit) {
    val viewModel: BudgetSetupViewModel = koinViewModel()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    var connecting by remember { mutableStateOf(false) }
    when (ready) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        true -> content()
        false -> if (connecting) {
            BackHandler { connecting = false }
            Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                com.firkat.fillin.feature.auth.ui.AuthAccountSection()
                com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionSection()
                TextButton(onClick = { connecting = false }) { Text("Назад") }
            }
        } else BudgetSetupScreen(viewModel, onConnect = { connecting = true })
    }
}

@Composable
fun BudgetSetupScreen(viewModel: BudgetSetupViewModel, onConnect: () -> Unit = viewModel::connect) {
    var balance by androidx.compose.runtime.saveable
        .rememberSaveable { mutableStateOf("0") }
    var date by androidx.compose.runtime.saveable
        .rememberSaveable { mutableStateOf(viewModel.defaultStartDate.toString()) }
    BackHandler(enabled = viewModel.creating && !viewModel.busy, onBack = viewModel::back)
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Fillin", style = MaterialTheme.typography.headlineLarge)
        Text(if (viewModel.creating) "Создание бюджета" else "Начало работы", style = MaterialTheme.typography.headlineSmall)
        if (viewModel.creating) {
            OutlinedTextField(balance, { balance = it }, label = { Text("Начальный баланс, ₸") }, enabled = !viewModel.busy, singleLine = true)
            OutlinedTextField(date, { date = it }, label = { Text("Дата начала учёта (ГГГГ-ММ-ДД)") }, enabled = !viewModel.busy, singleLine = true)
            Text("Бюджет сохраняется на устройстве. Облачную синхронизацию можно включить позже.")
            Button(onClick = { viewModel.create(balance, date) }, enabled = !viewModel.busy) { Text("Создать бюджет") }
            TextButton(onClick = viewModel::back, enabled = !viewModel.busy) { Text("Назад") }
        } else {
            Button(onClick = viewModel::chooseCreate, enabled = !viewModel.busy) { Text("Создать бюджет") }
            OutlinedButton(onClick = onConnect, enabled = !viewModel.busy) { Text("Подключиться к существующему") }
        }
        if (viewModel.busy) {
            CircularProgressIndicator()
            Text("Загрузка бюджета…")
        }
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
