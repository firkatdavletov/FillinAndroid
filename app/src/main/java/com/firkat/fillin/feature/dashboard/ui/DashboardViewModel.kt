package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.usecase.ObserveDashboard
import com.firkat.fillin.feature.sync.domain.BudgetSyncControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val observeDashboard: ObserveDashboard,
    private val sync: BudgetSyncControl,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()
    private var observation: Job? = null
    private var accessDenied = false

    init {
        viewModelScope.launch {
            sync.context.collect { context ->
                val denied = !context.keepLocalAccess && context.error in setOf("unauthorized", "access_denied", "scope_changed")
                accessDenied = denied
                _uiState.update {
                    it.copy(
                        dashboard = it.dashboard.takeIf { context.ready && !denied },
                        awaitingInitialSync = !context.ready,
                        isRefreshing = context.syncing,
                        error =
                            if (denied) {
                                DashboardError.Forbidden
                            } else {
                                it.error.takeUnless { error ->
                                    error ==
                                        DashboardError.Forbidden
                                }
                            },
                    )
                }
            }
        }
        observe()
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.Refresh, DashboardEvent.Retry -> {
                observeDashboard.requestSync()
                observe()
            }

            DashboardEvent.Resume -> {
                observe()
            }
        }
    }

    private fun observe() {
        observation?.cancel()
        observation =
            viewModelScope.launch {
                try {
                    observeDashboard().collect { dashboard ->
                        if (accessDenied) return@collect
                        _uiState.update {
                            it.copy(dashboard = dashboard, isLoading = false, error = null, awaitingInitialSync = false)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(dashboard = null, isLoading = false, error = DashboardError.LocalData) }
                }
            }
    }
}
