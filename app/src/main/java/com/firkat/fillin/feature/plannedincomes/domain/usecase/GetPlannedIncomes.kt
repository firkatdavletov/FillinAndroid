package com.firkat.fillin.feature.plannedincomes.domain.usecase

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository

class GetPlannedIncomes(private val repository: PlannedIncomesRepository) {
    fun observe() = repository.observePlannedIncomes()

    suspend operator fun invoke(): PlannedIncomes = repository.getPlannedIncomes()
}
