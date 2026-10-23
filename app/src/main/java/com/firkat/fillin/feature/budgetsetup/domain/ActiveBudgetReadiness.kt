package com.firkat.fillin.feature.budgetsetup.domain

import kotlinx.coroutines.flow.Flow

fun interface ActiveBudgetReadiness {
    /** Restores the current account's selection and follows its committed budget readiness. */
    fun observe(): Flow<Boolean>
}

enum class BudgetSetupMode { Local, Cloud }
