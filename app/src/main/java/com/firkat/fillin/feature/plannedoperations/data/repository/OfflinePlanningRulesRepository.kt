package com.firkat.fillin.feature.plannedoperations.data.repository

import com.firkat.fillin.feature.plannedoperations.domain.repository.*
import com.firkat.fillin.feature.sync.data.BudgetStore
import com.firkat.fillin.feature.sync.data.BudgetLocalFailure
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class OfflinePlanningRulesRepository(private val store: BudgetStore) : PlanningRulesRepository {
    override fun observeRule(id: String) = store.observePlanningRules(includeSettled = true).map { rules -> rules.firstOrNull { it.id == id } }
    override fun requestSync() = store.requestSync()
    override fun observeRules() = store.observePlanningRules().catch { e ->
        if (e is BudgetLocalFailure) throw if (e.code == "forbidden") PlannedOperationsException.Forbidden else PlannedOperationsException.InvalidRequest
        throw e
    }
}
