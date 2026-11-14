package com.firkat.fillin.feature.plannedoperations.domain.usecase

import com.firkat.fillin.feature.plannedoperations.domain.repository.PlanningRulesRepository

class ObservePlanningRules(private val repository: PlanningRulesRepository) {
    operator fun invoke() = repository.observeRules()
    fun observeRule(
        id: String
    ) = repository.observeRule(id)
    fun requestSync() = repository.requestSync()
}
