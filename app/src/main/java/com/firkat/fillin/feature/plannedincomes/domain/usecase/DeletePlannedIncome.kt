package com.firkat.fillin.feature.plannedincomes.domain.usecase

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository

class DeletePlannedIncome(
    private val repository: PlannedIncomesRepository,
) {
    suspend operator fun invoke(income: PlannedIncome) {
        repository.deletePlannedIncome(id = income.id, version = income.version)
    }
}
