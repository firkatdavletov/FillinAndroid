package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardException
import com.firkat.fillin.feature.dashboard.domain.usecase.GetDashboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val getDashboard: GetDashboard,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.Retry -> loadDashboard()
            DashboardEvent.Refresh -> loadDashboard(isRefreshing = true)
        }
    }

    private fun loadDashboard(isRefreshing: Boolean = false) {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return
        _uiState.update {
            it.copy(isLoading = !isRefreshing, isRefreshing = isRefreshing, error = null)
        }

        viewModelScope.launch {
            try {
                val dashboard = getDashboard()
                _uiState.update {
                    it.copy(
                        dashboard = dashboard,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: DashboardException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = exception.toUiError(),
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = DashboardError.Unknown,
                    )
                }
            }
        }
    }
}

private fun DashboardException.toUiError(): DashboardError = when (this) {
    DashboardException.MissingApiToken -> DashboardError.MissingApiToken
    DashboardException.Unauthorized -> DashboardError.Unauthorized
    DashboardException.Forbidden -> DashboardError.Forbidden
    DashboardException.Network -> DashboardError.Network
    DashboardException.InvalidResponse -> DashboardError.InvalidResponse
    DashboardException.Unknown -> DashboardError.Unknown
}
