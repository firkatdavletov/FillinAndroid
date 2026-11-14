package com.firkat.fillin.feature.dashboard.domain

import com.firkat.fillin.feature.dashboard.domain.model.Dashboard
import com.firkat.fillin.feature.dashboard.domain.model.DashboardDataState
import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.sync.domain.BudgetSyncControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import java.io.Closeable

/** One calculation stream for the host and all widget features. No UI owns the snapshot. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardDataSource(
    sync: BudgetSyncControl,
    private val repository: DashboardRepository,
    private val scope: CoroutineScope,
) : Closeable {
    private val reload = MutableStateFlow(0L)
    private val context =
        sync.context.shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(0, 0),
            replay = 1,
        )

    private data class Access(
        val ready: Boolean,
        val denied: Boolean,
    )

    private data class Observation(
        val dashboard: Dashboard? = null,
        val failed: Boolean = false,
    )

    private val observation =
        context
            .map {
                Access(
                    ready = it.ready,
                    denied = !it.keepLocalAccess && it.error in setOf("unauthorized", "access_denied", "scope_changed"),
                )
            }.distinctUntilChanged()
            .flatMapLatest { access ->
                if (!access.ready || access.denied) {
                    flowOf(Observation())
                } else {
                    reload
                        .flatMapLatest {
                            repository
                                .observeDashboard()
                                .map { Observation(dashboard = it) }
                                .catch { emit(Observation(failed = true)) }
                        }.onStart { emit(Observation()) }
                }
            }

    val state: StateFlow<DashboardDataState> =
        combine(context, observation) { context, observation ->
            val denied = !context.keepLocalAccess && context.error in setOf("unauthorized", "access_denied", "scope_changed")
            when {
                denied -> {
                    DashboardDataState(status = DashboardStatus.Forbidden)
                }

                !context.ready -> {
                    DashboardDataState()
                }

                observation.failed -> {
                    DashboardDataState(status = DashboardStatus.LocalError)
                }

                observation.dashboard == null -> {
                    DashboardDataState(status = DashboardStatus.Loading)
                }

                else -> {
                    DashboardDataState(
                        dashboard = observation.dashboard,
                        status = DashboardStatus.Ready,
                    )
                }
            }.copy(isRefreshing = context.syncing)
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(0, 0),
            initialValue = DashboardDataState(),
        )

    fun resume() {
        reload.update { it + 1 }
    }

    fun refresh() {
        repository.requestSync()
        resume()
    }

    override fun close() {
        scope.cancel()
    }
}
