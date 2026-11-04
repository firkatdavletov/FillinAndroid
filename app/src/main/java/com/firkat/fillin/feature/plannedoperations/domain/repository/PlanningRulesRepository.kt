package com.firkat.fillin.feature.plannedoperations.domain.repository

import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule
import kotlinx.coroutines.flow.Flow

interface PlanningRulesRepository {
    fun observeRules(): Flow<List<PlanningRule>>
    fun requestSync()
}
