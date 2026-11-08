package com.firkat.fillin.feature.plannedoperations.domain.repository

import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PlanningRulesRepository {
    fun observeRules(): Flow<List<PlanningRule>>
    fun observeRule(id: String): Flow<PlanningRule?> = observeRules().map { rules -> rules.firstOrNull { it.id == id } }
    fun requestSync()
}
