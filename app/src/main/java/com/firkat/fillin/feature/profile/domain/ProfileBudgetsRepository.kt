package com.firkat.fillin.feature.profile.domain

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
)

interface ProfileBudgetsRepository {
    fun observe(): Flow<List<ProfileBudget>>
    suspend fun refresh()
    suspend fun select(id: String)
    suspend fun createLocal(balance: BigDecimal, startedOn: LocalDate)
    suspend fun createLocal(name: String, balance: BigDecimal, startedOn: LocalDate)
}
