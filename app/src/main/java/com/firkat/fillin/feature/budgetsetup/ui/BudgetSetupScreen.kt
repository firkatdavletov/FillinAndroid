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
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.ui.*
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionSection
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetsetup.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

enum class BudgetSetupStep { Welcome, LocalSetup, CloudSetup }

class BudgetSetupViewModel(
    private val session: BudgetSetupRepository,
    private val createLocal: CreateLocalBudget = CreateLocalBudget(session),
    private val connectExisting: ConnectExistingBudget = ConnectExistingBudget(session),
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    val defaultStartDate = session.today
    val ready = session.ready.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var step by mutableStateOf(savedState.get<String>("step")?.let(BudgetSetupStep::valueOf) ?: BudgetSetupStep.Welcome)
        private set
    private var lastAuthUid: String? = savedState["authUid"]
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun chooseCreate() {
        if (!busy) changeStep(BudgetSetupStep.LocalSetup)
    }

    fun chooseCloud() {
        if (!busy) changeStep(BudgetSetupStep.CloudSetup)
    }

    fun authSessionChanged(session: AuthSession) {
        if (session == AuthSession.Loading) return
        val uid = (session as? AuthSession.Authenticated)?.user?.uid
        if (uid != lastAuthUid) {
            if (uid != null && step != BudgetSetupStep.LocalSetup) changeStep(BudgetSetupStep.CloudSetup)
            else if (uid == null && step == BudgetSetupStep.CloudSetup) changeStep(BudgetSetupStep.Welcome)
            lastAuthUid = uid
            savedState["authUid"] = uid
        }
    }

    fun back() {
        if (!busy) {
            changeStep(BudgetSetupStep.Welcome)
        }
    }

    private fun changeStep(value: BudgetSetupStep) {
        step = value
        savedState["step"] = value.name
        error = null
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
                changeStep(BudgetSetupStep.Welcome)
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
        "creation_pending" -> "Создание предыдущего бюджета ещё не завершено. Продолжите его создание."
        "creation_conflict" -> "Сервер отклонил повтор создания. Запрос сохранён; обратитесь к разработчику."
        "invalid_creation" -> "Проверьте название, начальный баланс и дату бюджета."
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
    // Budget profiles own isolated ViewModelStores, without an Activity SavedStateRegistryOwner.
    val savedState = rememberSaveable(saver = BudgetSetupSavedStateSaver) { SavedStateHandle() }
    val viewModel: BudgetSetupViewModel = koinViewModel(parameters = { parametersOf(savedState) })
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (ready) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            true -> content()
            false -> BudgetSetupScreen(viewModel)
        }
    }
}

private val BudgetSetupSavedStateSaver = mapSaver(
    save = { state: SavedStateHandle -> state.keys().associateWith { state.get<Any?>(it) } },
    restore = { SavedStateHandle(it) },
)

@Composable
fun BudgetSetupScreen(
    viewModel: BudgetSetupViewModel,
    authViewModel: AuthViewModel = koinViewModel(),
    googleSignIn: GoogleSignInLauncher = koinInject(),
    connectionViewModel: BudgetConnectionViewModel? = null,
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    var balance by rememberSaveable { mutableStateOf("0") }
    var date by rememberSaveable { mutableStateOf(viewModel.defaultStartDate.toString()) }
    LaunchedEffect(authState.session) { viewModel.authSessionChanged(authState.session) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (viewModel.step) {
            BudgetSetupStep.Welcome -> {
                GoogleSignInEffect(authViewModel, googleSignIn)
                BudgetWelcomeContent(
                    state = authState,
                    onSignIn = {
                        if (authState.session is AuthSession.Authenticated) viewModel.chooseCloud()
                        else authViewModel.signIn()
                    },
                    onContinueWithoutAuth = viewModel::chooseCreate,
                )
            }
            BudgetSetupStep.LocalSetup -> {
                BackHandler { viewModel.back() }
                LocalBudgetSetupContent(viewModel, balance, { balance = it }, date, { date = it })
            }
            BudgetSetupStep.CloudSetup -> CloudBudgetSetupContent(
                viewModel = connectionViewModel ?: koinViewModel(
                    key = "onboarding-budget-connection",
                    parameters = { parametersOf(true) },
                ),
                onBack = viewModel::back,
            )
        }
    }
}

@Composable
fun BudgetWelcomeContent(
    state: AuthUiState,
    onSignIn: () -> Unit,
    onContinueWithoutAuth: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Fillin", style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.budget_setup_welcome), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Button(
            onClick = onSignIn,
            enabled = !state.busy && state.session != AuthSession.Loading && state.session != AuthSession.Unavailable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.auth_google_sign_in), textAlign = TextAlign.Center)
        }
        OutlinedButton(onClick = onContinueWithoutAuth, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.budget_setup_continue_without_auth), textAlign = TextAlign.Center)
        }
        if (state.busy || state.session == AuthSession.Loading) CircularProgressIndicator()
        if (state.session == AuthSession.Unavailable) {
            Text(stringResource(R.string.auth_not_configured), textAlign = TextAlign.Center)
        }
        state.error?.let { Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun LocalBudgetSetupContent(
    viewModel: BudgetSetupViewModel,
    balance: String,
    onBalanceChange: (String) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
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
        TextButton(onClick = viewModel::back, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.navigation_back)) }
        if (viewModel.busy) {
            CircularProgressIndicator()
            Text(stringResource(R.string.budget_setup_loading))
        }
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CloudBudgetSetupContent(viewModel: BudgetConnectionViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { if (!state.busy) onBack() }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Fillin", style = MaterialTheme.typography.headlineLarge)
        BudgetConnectionSection(viewModel, showCreateCancel = false)
        TextButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.navigation_back)) }
    }
}
