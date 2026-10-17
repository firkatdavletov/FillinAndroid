package com.firkat.fillin.feature.plannedoperations.domain.usecase

import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsRepository

class GetPlannedOperations(private val repository: PlannedOperationsRepository) {
    suspend operator fun invoke(filter: PlannedOperationsFilter) = repository.getPlannedOperations(filter)
}
