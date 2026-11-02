package com.firkat.fillin.feature.budgetsetup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetconnection.ui.interactor.*
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import com.firkat.fillin.feature.budgetsharing.ui.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun BudgetSetupScreen(
    mode: BudgetSetupMode,
    onBack: () -> Unit,
    localViewModel: BudgetSetupViewModel? = null,
    cloudViewModel: BudgetConnectionViewModel? = null,
    joinViewModel: FamilyBudgetJoinViewModel? = null,
) {
    when (mode) {
        BudgetSetupMode.Local -> LocalBudgetSetup(localViewModel ?: koinViewModel(), onBack)
        BudgetSetupMode.Cloud -> CloudBudgetSetup(cloudViewModel ?: koinViewModel(
            key = "onboarding-budget-connection", parameters = { parametersOf(true) },
        ), joinViewModel, onBack)
    }
}

@Composable
private fun LocalBudgetSetup(viewModel: BudgetSetupViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var snapshot by rememberSaveable { mutableStateOf(listOf(state.balance, state.date)) }
    LaunchedEffect(viewModel) {
        viewModel.onAction(BudgetSetupAction.RestoreForm(snapshot[0], snapshot[1]))
        viewModel.state.collect { snapshot = listOf(it.balance, it.date) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { if (it == BudgetSetupEffect.Back) back() }
        }
    }
    val onBackClick = { viewModel.onAction(BudgetSetupAction.Back) }
    BackHandler(onBack = onBackClick)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Image(
            painter = painterResource(R.drawable.saldomi_auth_background), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        LocalBudgetSetupContent(
            busy = state.busy, error = state.error,
            onCreateClick = { viewModel.onAction(BudgetSetupAction.Create) },
            balance = state.balance, onBalanceChange = { viewModel.onAction(BudgetSetupAction.BalanceChanged(it)) },
            date = state.date, onDateChange = { viewModel.onAction(BudgetSetupAction.DateChanged(it)) },
            onBack = onBackClick,
        )
    }
}

@Composable
private fun CloudBudgetSetup(viewModel: BudgetConnectionViewModel, joinViewModel: FamilyBudgetJoinViewModel?, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Match the existing lifetime: join observation starts when its section is first opened.
    // Leaving the section keeps its ViewModel/command alive in the budget's ViewModelStore.
    val joining = if (state.joiningFamily) joinViewModel ?: koinViewModel<FamilyBudgetJoinViewModel>() else null
    val join = joining?.state?.collectAsStateWithLifecycle()?.value ?: FamilyJoinState()
    // Only a restoration snapshot is saved here; edits and section selection belong to the reducer.
    // The enclosing ActiveBudgetContent keys composition/saveable state to the budget runtime.
    var snapshot by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var sectionSnapshot by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(viewModel) {
        var restored = false
        var sectionRestored = false
        viewModel.state.collect { value ->
            if (value.session == AuthSession.Loading) return@collect
            val uid = (value.session as? AuthSession.Authenticated)?.user?.uid.orEmpty()
            if (snapshot?.firstOrNull() != uid) snapshot = null
            if (sectionSnapshot?.firstOrNull() != uid) sectionSnapshot = null
            // Section selection also survives a failed first load with no cached budgets.
            if (!sectionRestored) {
                sectionRestored = true
                sectionSnapshot?.let {
                    val showJoin = it[1] == "true"
                    if (showJoin != value.joiningFamily) {
                        viewModel.onAction(BudgetConnectionAction.RestoreSection(uid.takeIf(String::isNotEmpty), showJoin))
                        return@collect
                    }
                }
            }
            sectionSnapshot = listOf(uid, value.joiningFamily.toString())
            if (value.loaded && !value.busy) {
                if (!restored) {
                    restored = true
                    snapshot?.let {
                        if (value.pending == null && value.draft != null) {
                            viewModel.onAction(BudgetConnectionAction.Restore(
                                uid, BudgetCreationDraft(it[1], it[2], it[3]),
                            ))
                        }
                    }
                }
                val current = viewModel.state.value
                snapshot = current.draft?.let { listOf(uid, it.name, it.openingBalance, it.accountingStartedOn) }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { if (it == BudgetConnectionEffect.Back) back() }
        }
    }
    val onBackClick = { viewModel.onAction(BudgetConnectionAction.Back) }
    BackHandler(onBack = onBackClick)
    CloudBudgetSetupContent(
        session = state.session, budgets = state.budgets, busy = state.busy, loaded = state.loaded,
        draft = state.draft, pending = state.pending, activeBudgetId = state.activeBudgetId,
        showingSaved = state.showingSaved, error = state.error, joiningFamily = state.joiningFamily,
        joinSession = join.session, joinCode = join.code, joinBusy = join.busy, joinError = join.error, joinedName = join.joinedName,
        onDraftChange = { viewModel.onAction(BudgetConnectionAction.EditDraft(it)) },
        onCreateClick = { viewModel.onAction(BudgetConnectionAction.Create) },
        onChooseCreateClick = { viewModel.onAction(BudgetConnectionAction.ChooseCreate()) },
        onRefreshClick = { viewModel.onAction(BudgetConnectionAction.Refresh) },
        onBudgetClick = { viewModel.onAction(BudgetConnectionAction.Connect(it)) },
        onJoinSectionClick = { viewModel.onAction(BudgetConnectionAction.ShowJoin(it)) },
        onCodeChange = { joining?.onAction(FamilyJoinAction.EditCode(it)) },
        onJoinClick = { joining?.onAction(FamilyJoinAction.Join) }, onBack = onBackClick,
    )
}
