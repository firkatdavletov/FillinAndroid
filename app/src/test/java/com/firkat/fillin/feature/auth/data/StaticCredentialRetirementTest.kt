package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.operationhistory.data.remote.CreateOperationDto
import com.firkat.fillin.feature.operationhistory.data.sync.SyncBlockedException
import com.firkat.fillin.feature.sync.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StaticCredentialRetirementTest {
    @Test fun `build configuration exposes no static mobile credential`() {
        assertFalse(BuildConfig::class.java.declaredFields.any { it.name == "MOBILE_API_TOKEN" })
    }

    @Test fun `local and retired transports reject requests before any network access`() = runBlocking {
        val command = BudgetSyncCommand("request", "budget", "member", "CREATE_OPERATION", "entry", "2026-10-23")
        val calls: List<suspend () -> Any> = listOf(
            { LocalOnlyCloudApi.budget("unused") },
            { LocalOnlyCloudApi.create("unused", CreateCloudBudget("request", "budget", "member", "0.00", "2026-10-23", emptyList())) },
            { LocalOnlyCloudApi.changes("unused", null) },
            { LocalOnlyCloudApi.command("unused", command) },
            { RetiredBudgetSyncApi.command("unused", command) },
            { RetiredOperationSyncApi.changes("unused", null) },
            { RetiredOperationSyncApi.create("unused", CreateOperationDto("request", "budget", "member", "EXPENSE", "category", "1.00", "2026-10-23")) },
        )
        calls.forEach { call ->
            val failure = runCatching { call() }.exceptionOrNull() as SyncBlockedException
            assertEquals("sign_in_required", failure.code)
        }
    }
}
