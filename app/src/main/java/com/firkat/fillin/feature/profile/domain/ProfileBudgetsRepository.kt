package com.firkat.fillin.feature.profile.domain

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

data class ProfileBudget(
    val id: String,
    val name: String?,
    val family: Boolean,
    val selected: Boolean,
    val canManage: Boolean = !family,
    val pendingRename: Boolean = false,
    val currency: BudgetCurrency = BudgetCurrency.KZT,
)

interface ProfileBudgetsRepository {
    fun observe(): Flow<List<ProfileBudget>>
    suspend fun refresh()
    suspend fun select(id: String)
    suspend fun createLocal(balance: BigDecimal, startedOn: LocalDate)
    suspend fun createLocal(balance: BigDecimal, startedOn: LocalDate, currency: BudgetCurrency) =
        createLocal(balance, startedOn)
    suspend fun createLocal(name: String, balance: BigDecimal, startedOn: LocalDate)
}
