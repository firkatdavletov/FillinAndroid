package com.firkat.fillin.feature.budgetsetup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.firkat.fillin.feature.budgetsetup.ui.interactor.BudgetSetupAction
import com.firkat.fillin.feature.budgetsetup.ui.interactor.BudgetSetupEffect
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupAuthTarget
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupPage
import com.firkat.fillin.feature.budgettransfer.ui.BudgetTransferMessage
import com.firkat.fillin.feature.budgettransfer.ui.BudgetTransferViewModel
import com.firkat.fillin.feature.budgettransfer.ui.rememberBudgetImportPicker
import com.firkat.fillin.ui.component.showSaldomiSnackbar

@Composable
internal fun BudgetSetupScreen(
    viewModel: BudgetSetupViewModel,
    onBack: () -> Unit,
    onSignIn: (SetupAuthTarget) -> Unit,
    onComplete: () -> Unit,
    onPageChange: (SetupPage) -> Unit,
    fromProfile: Boolean,
    page: SetupPage? = null,
    isActive: Boolean = true,
    transferViewModel: BudgetTransferViewModel? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canCreateFromFile by viewModel.canCreateFromFile.collectAsStateWithLifecycle(initialValue = false)
    val transferState = transferViewModel?.state?.collectAsStateWithLifecycle()?.value
    val openImportPicker = transferViewModel?.let { rememberBudgetImportPicker(it, nameSuffix = "") }
    val importing = transferState?.busy == true
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel, fromProfile, isActive) {
        if (isActive) viewModel.onAction(BudgetSetupAction.Start)
    }
    LaunchedEffect(viewModel, lifecycle, isActive) {
        if (!isActive) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect {
                if (it ==
                    BudgetSetupEffect.Exit
                ) {
                    viewModel.onAction(BudgetSetupAction.Reset)
                    back()
                }
            }
        }
    }
    LaunchedEffect(state.error, lifecycle, resources, isActive) {
        if (!isActive) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state.error?.let {
                snackbar.showSaldomiSnackbar(accessErrorResource(it)?.let(resources::getString) ?: it)
                viewModel.onAction(BudgetSetupAction.ErrorShown)
            }
        }
    }
    LaunchedEffect(state.completed, isActive) {
        if (isActive && state.completed) {
            viewModel.onAction(BudgetSetupAction.Reset)
            onComplete()
        }
    }
    LaunchedEffect(state.authTarget, state.uid, isActive) {
        if (!isActive) return@LaunchedEffect
        state.authTarget?.let { target ->
            if (state.uid != null) {
                viewModel.onAction(BudgetSetupAction.AuthReturned)
            } else if (!state.authLaunched) {
                viewModel.onAction(BudgetSetupAction.AuthLaunched)
                onSignIn(target)
            }
        }
    }
    LaunchedEffect(state.page, state.initialized, isActive) {
        if (isActive && state.initialized) onPageChange(state.page)
    }
    BackHandler(enabled = isActive) {
        if (!importing) {
            viewModel.onAction(
                if (state.currencyPickerOpen) BudgetSetupAction.CloseCurrencyPicker else BudgetSetupAction.Back
            )
        }
    }
    BudgetSetupContent(
        state.copy(page = page ?: state.page),
        onAction = { if (!importing) viewModel.onAction(it) },
        snackbar = snackbar,
        fromProfile = fromProfile,
        fileImportBusy = importing,
        fileImportContent = {
            if (!fromProfile && openImportPicker != null && (canCreateFromFile || importing)) {
                BudgetSetupFileButton(
                    enabled = isActive && state.editable && !importing,
                    working = transferState?.working == true,
                    onClick = { if (viewModel.state.value.editable) openImportPicker() }
                )
            }
            if (!fromProfile) {
                transferState?.result?.let { BudgetTransferMessage(it) }
            }
        }
    )
}
