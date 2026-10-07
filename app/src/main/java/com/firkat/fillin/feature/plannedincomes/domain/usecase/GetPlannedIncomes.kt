package com.firkat.fillin.feature.plannedincomes.domain.usecase

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository

class GetPlannedIncomes(
    private val repository: PlannedIncomesRepository,
) {
    suspend operator fun invoke(): PlannedIncomes = repository.getPlannedIncomes()
}
