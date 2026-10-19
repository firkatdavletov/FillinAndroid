package com.firkat.fillin.feature.sync.domain

interface CloudSyncSettings {
    suspend fun enable()

    suspend fun disable()
}

open class BudgetConnectionFailure(
    val code: String,
) : Exception(code)
