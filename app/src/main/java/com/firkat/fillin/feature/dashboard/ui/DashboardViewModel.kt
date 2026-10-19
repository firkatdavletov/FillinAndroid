package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(private val source: DashboardDataSource) : ViewModel() {
    val uiState = source.state.map {
        DashboardUiState(it.dashboard?.date, it.status, it.isRefreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), DashboardUiState())

    fun onEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.Refresh, DashboardEvent.Retry -> source.refresh()
            DashboardEvent.Resume -> source.resume()
        }
    }
}
