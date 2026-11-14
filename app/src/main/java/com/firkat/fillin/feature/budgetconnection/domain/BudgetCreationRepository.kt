package com.firkat.fillin.feature.budgetconnection.domain

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import java.math.BigDecimal
import java.time.LocalDate

data class BudgetCreationDraft(
    val name: String,
    val openingBalance: String,
    val accountingStartedOn: String,
    val currency: BudgetCurrency = BudgetCurrency.KZT
) {
    fun validated(
        today: LocalDate
    ): BudgetCreationDraft {
        require(validationErrors(today).isEmpty)
        val title = name.trim()
        val balance = openingBalance.trim().replace(',', '.').toBigDecimal()
        val date = LocalDate.parse(accountingStartedOn.trim())
        return BudgetCreationDraft(title, balance.setScale(2).toPlainString(), date.toString(), currency)
    }
}

data class BudgetCreationErrors(val name: Boolean = false, val balance: Boolean = false, val date: Boolean = false) {
    val isEmpty get() = !name && !balance && !date
}

internal fun budgetCreationFieldErrors(
    name: String,
    openingBalance: String
): BudgetCreationErrors = BudgetCreationErrors(
    name = name.trim().let { it.length !in 1..80 || it.any(Char::isISOControl) },
    balance = openingBalance.length > 64 ||
        !runCatching {
            val amount = openingBalance.trim().replace(',', '.').toBigDecimal()
            amount.scale() <= 2 && amount.abs() <= BigDecimal("99999999999999999.99")
        }.getOrDefault(false)
)

fun BudgetCreationDraft.validationErrors(
    today: LocalDate
): BudgetCreationErrors = budgetCreationFieldErrors(name, openingBalance).copy(
    date = !runCatching {
        val date = LocalDate.parse(accountingStartedOn.trim())
        date.year >= 1 && date <= today
    }.getOrDefault(false)
)

data class PendingBudgetCreation(val requestId: String, val draft: BudgetCreationDraft)

interface BudgetCreationRepository {
    suspend fun pending(
        uid: String
    ): PendingBudgetCreation?
    suspend fun create(
        uid: String,
        draft: BudgetCreationDraft
    )
    suspend fun resume(
        uid: String
    )
}

interface BudgetCreationStore {
    suspend fun pending(
        uid: String
    ): PendingBudgetCreation?
    suspend fun save(
        uid: String,
        request: PendingBudgetCreation
    ): PendingBudgetCreation
    suspend fun complete(
        uid: String,
        requestId: String
    )
}
