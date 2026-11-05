package com.firkat.fillin.feature.budgetsetup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import com.firkat.fillin.ui.component.showSaldomiSnackbar

@Composable
internal fun BudgetSetupScreen(
    viewModel: BudgetSetupViewModel,
    onBack: () -> Unit,
    onSignIn: (SetupAuthTarget) -> Unit,
    onComplete: () -> Unit,
    onPageChange: (SetupPage) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) { viewModel.onAction(BudgetSetupAction.Start) }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { if (it == BudgetSetupEffect.Exit) { viewModel.onAction(BudgetSetupAction.Reset); back() } }
        }
    }
    LaunchedEffect(state.error, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state.error?.let {
                snackbar.showSaldomiSnackbar(it)
                viewModel.onAction(BudgetSetupAction.ErrorShown)
            }
        }
    }
    LaunchedEffect(state.completed) {
        if (state.completed) { viewModel.onAction(BudgetSetupAction.Reset); onComplete() }
    }
    LaunchedEffect(state.authTarget, state.uid) {
        state.authTarget?.let { target ->
            if (state.uid != null) viewModel.onAction(BudgetSetupAction.AuthReturned)
            else if (!state.authLaunched) {
                viewModel.onAction(BudgetSetupAction.AuthLaunched)
                onSignIn(target)
            }
        }
    }
    LaunchedEffect(state.page, state.initialized) { if (state.initialized) onPageChange(state.page) }
    BackHandler {
        viewModel.onAction(if (state.currencyPickerOpen) BudgetSetupAction.CloseCurrencyPicker else BudgetSetupAction.Back)
    }
    BudgetSetupContent(state, viewModel::onAction, snackbar)
}
