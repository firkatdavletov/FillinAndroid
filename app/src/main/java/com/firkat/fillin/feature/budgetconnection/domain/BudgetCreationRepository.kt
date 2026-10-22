package com.firkat.fillin.feature.budgetconnection.domain

import java.math.BigDecimal
import java.time.LocalDate

data class BudgetCreationDraft(val name: String, val openingBalance: String, val accountingStartedOn: String) {
    fun validated(today: LocalDate): BudgetCreationDraft {
        val title = name.trim()
        require(title.length in 1..80 && title.none(Char::isISOControl))
        require(openingBalance.length <= 64)
        val balance = openingBalance.trim().replace(',', '.').toBigDecimal()
        require(balance.scale() <= 2 && balance.abs() <= BigDecimal("99999999999999999.99"))
        val date = LocalDate.parse(accountingStartedOn.trim())
        require(date.year >= 1 && date <= today)
        return BudgetCreationDraft(title, balance.setScale(2).toPlainString(), date.toString())
    }
}

data class PendingBudgetCreation(val requestId: String, val draft: BudgetCreationDraft)

interface BudgetCreationRepository {
    suspend fun pending(uid: String): PendingBudgetCreation?
    suspend fun create(uid: String, draft: BudgetCreationDraft)
    suspend fun resume(uid: String)
}

interface BudgetCreationStore {
    suspend fun pending(uid: String): PendingBudgetCreation?
    suspend fun save(uid: String, request: PendingBudgetCreation): PendingBudgetCreation
    suspend fun complete(uid: String, requestId: String)
}
