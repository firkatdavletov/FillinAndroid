package com.firkat.fillin.feature.plannedoperations.data.repository

import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.feature.plannedoperations.domain.repository.*
import com.firkat.fillin.feature.sync.data.BudgetLocalFailure
import com.firkat.fillin.feature.sync.data.BudgetStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

class OfflinePlannedOperationsRepository(private val store: BudgetStore) : PlannedOperationsRepository {
    override fun requestSync() = store.requestSync()

    override fun observePlans(
        filter: PlannedOperationsFilter
    ) = store.observePlans(filter).catch { e ->
        if (e is BudgetLocalFailure) {
            throw if (e.code ==
                "forbidden"
            ) {
                PlannedOperationsException.Forbidden
            } else {
                PlannedOperationsException.InvalidRequest
            }
        }
        throw e
    }

    override suspend fun getPlannedOperations(
        filter: PlannedOperationsFilter
    ) = observePlans(filter).first()
}
