package com.firkat.fillin.feature.plannedoperations.domain.repository

import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperations
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter

interface PlannedOperationsRepository {
    fun observePlans(
        filter: PlannedOperationsFilter
    ): kotlinx.coroutines.flow.Flow<PlannedOperations> = kotlinx.coroutines.flow.flow {
        emit(getPlannedOperations(filter))
    }

    fun requestSync() = Unit

    suspend fun getPlannedOperations(
        filter: PlannedOperationsFilter
    ): PlannedOperations
}

sealed class PlannedOperationsException : Exception() {
    data object MissingApiToken : PlannedOperationsException()

    data object Unauthorized : PlannedOperationsException()

    data object Forbidden : PlannedOperationsException()

    data object InvalidRequest : PlannedOperationsException()

    data object Network : PlannedOperationsException()

    data object InvalidResponse : PlannedOperationsException()

    data object Unknown : PlannedOperationsException()
}
