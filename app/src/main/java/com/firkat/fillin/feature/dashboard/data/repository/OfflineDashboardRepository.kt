package com.firkat.fillin.feature.dashboard.data.repository

import com.firkat.fillin.feature.dashboard.domain.calculation.CalculateDashboard
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.sync.data.BudgetProjection
import com.firkat.fillin.feature.sync.data.BudgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.ZoneId

class OfflineDashboardRepository(
    private val database: OperationDatabase,
    private val store: BudgetStore,
    private val json: Json,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : DashboardRepository {
    override fun requestSync() = store.requestSync()

    override fun observeDashboard() =
        combine(database.budget().observeContent(), dashboardDayChanges(clock)) {
            content,
            _,
            ->
            if (content == null || !(content.sync.localReady || content.sync.initialSyncComplete) || content.sync.protocolVersion < 3 ||
                (content.sync.protocolVersion < 4 && content.sync.errorCode in setOf("unauthorized", "access_denied", "scope_changed"))
            ) {
                null
            } else {
                val asOf = clock.instant()
                val today = asOf.atZone(ZONE).toLocalDate()
                CalculateDashboard.calculate(BudgetProjection(content, json, today).dashboardInput(asOf))
            }
        }.filterNotNull().distinctUntilChanged().flowOn(Dispatchers.Default)

    companion object {
        private val ZONE = ZoneId.of("Asia/Almaty")
    }
}

internal fun dashboardDayChanges(clock: Clock) =
    flow {
        val zone = ZoneId.of("Asia/Almaty")
        while (true) {
            emit(Unit)
            val now = clock.instant()
            val next =
                now
                    .atZone(zone)
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
        }
    }
