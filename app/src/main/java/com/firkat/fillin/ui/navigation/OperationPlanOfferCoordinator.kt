package com.firkat.fillin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetRuntime
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOfferStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private val offerTimeout = 1.seconds

@Composable
internal fun OperationPlanOfferCoordinator(
    navigation: AppNavigationViewModel,
    budgets: ActiveBudgetManager,
    runtime: BudgetRuntime?,
    readOnly: Boolean
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val request = navigation.pendingPlanOffer
    val atSource = navigation.backStack.lastOrNull() == AppRoute.Main
    DisposableEffect(lifecycle, navigation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) navigation.cancelPlanOffer()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(request, atSource, runtime, readOnly, lifecycle) {
        if (request == null) return@LaunchedEffect
        val sameBudget = runtime?.scope?.id == request.scopeId
        val isForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val atEditableSource = atSource && !readOnly && isForeground
        val hasOffer = if (atEditableSource && sameBudget) {
            try {
                withTimeoutOrNull(offerTimeout - request.started.elapsedNow()) {
                    runtime.retain()?.use {
                        it.scope.get<OperationPlanOfferStore>().prepareOffer(request.operationId)
                    } == true
                } == true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        val inTime = request.started.elapsedNow() < offerTimeout
        val stillForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val canShow = hasOffer && inTime && stillForeground
        navigation.finishPlanOffer(request, canShow && budgets.active.value === runtime)
    }
}
