package com.firkat.fillin.feature.dashboard.domain

import com.firkat.fillin.feature.dashboard.domain.model.Dashboard
import com.firkat.fillin.feature.dashboard.domain.model.DashboardDataState
import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.sync.domain.BudgetSyncControl
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*

/** One calculation stream for the host and all widget features. No UI owns the snapshot. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardDataSource(
    private val repository: DashboardRepository,
    sync: BudgetSyncControl,
    private val scope: CoroutineScope,
) : Closeable {
    private val reload = MutableStateFlow(0L)
    private val context = sync.context.shareIn(scope, SharingStarted.WhileSubscribed(0, 0), replay = 1)
    private data class Access(val ready: Boolean, val denied: Boolean)
    private data class Observation(val dashboard: Dashboard? = null, val failed: Boolean = false)

    private val observation = context.map {
        Access(it.ready, !it.keepLocalAccess && it.error in setOf("unauthorized", "access_denied", "scope_changed"))
    }.distinctUntilChanged().flatMapLatest { access ->
        if (!access.ready || access.denied) {
            flowOf(Observation())
        } else {
            reload.flatMapLatest {
                repository.observeDashboard().map { Observation(dashboard = it) }
                    .catch { emit(Observation(failed = true)) }
            }.onStart { emit(Observation()) }
        }
    }

    val state: StateFlow<DashboardDataState> = combine(context, observation) { context, observation ->
        val denied = !context.keepLocalAccess && context.error in setOf("unauthorized", "access_denied", "scope_changed")
        when {
            denied -> DashboardDataState(status = DashboardStatus.Forbidden)
            !context.ready -> DashboardDataState()
            observation.failed -> DashboardDataState(status = DashboardStatus.LocalError)
            observation.dashboard == null -> DashboardDataState(status = DashboardStatus.Loading)
            else -> DashboardDataState(observation.dashboard, DashboardStatus.Ready)
        }.copy(isRefreshing = context.syncing)
    }.stateIn(scope, SharingStarted.WhileSubscribed(0, 0), DashboardDataState())

    fun resume() { reload.update { it + 1 } }

    fun refresh() {
        repository.requestSync()
        resume()
    }

    override fun close() { scope.cancel() }
}
