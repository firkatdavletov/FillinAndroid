package com.firkat.fillin.feature.budgetsetup.domain

import java.math.BigDecimal
import java.time.LocalDate

/** Creates and selects a complete local budget without making network requests. */
fun interface LocalBudgetCreator {
    suspend fun createLocal(name: String?, balance: BigDecimal, startedOn: LocalDate)
}
