package com.firkat.fillin.feature.profile

import androidx.room.Room
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.profile.data.AccountCloudEraser
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AccountCloudEraserTest {
    @Test fun deletionErasesCloudFilesIncludingArchivesAndPreservesOtherAccountsAndLocal() = runBlocking {
        BudgetConnectionDeviceTest.Environment().use { env ->
            env.manager.restore()
            val local = env.manager.active.value!!.profile
            env.database(local) { it.operations().state() }
            val first = env.registry.registerCloud("owner", "budget-a", "MEMBER")
            val id = UUID.randomUUID().toString()
            val replacement = BudgetProfile(id, "budget-$id.db", BudgetProfileKind.CLOUD, "owner", "budget-a", "MEMBER")
            env.registry.replaceCloudMembership(first.id, replacement)
            val other = env.registry.registerCloud("other", "budget-b", "OWNER")
            listOf(first, replacement, other).forEach { profile -> env.database(profile) { it.operations().state() } }
            val database = Room.databaseBuilder(
                env.context,
                BudgetProfileDatabase::class.java,
                env.registryName
            ).build()
            try {
                val eraser = AccountCloudEraser(env.context, database, env.manager)
                eraser.erase("owner")
                eraser.erase("owner")
                assertTrue(database.profiles().allCloud("owner").isEmpty())
                assertFalse(env.context.getDatabasePath(first.databaseName).exists())
                assertFalse(env.context.getDatabasePath(replacement.databaseName).exists())
                assertTrue(env.context.getDatabasePath(other.databaseName).exists())
                assertTrue(env.context.getDatabasePath(local.databaseName).exists())
                assertNotNull(env.registry.find(other.id))
                assertNotNull(env.registry.find(local.id))
            } finally {
                database.close()
            }
        }
    }

    @Test fun cleanupWaitsUntilInFlightLeaseIsReleased() = runBlocking {
        BudgetConnectionDeviceTest.Environment().use { env ->
            env.auth.login("owner")
            env.manager.restore()
            val cloud = env.registry.registerCloud("owner", "budget-a", "OWNER")
            env.manager.select(cloud.id, "owner")
            val lease = env.manager.acquire(cloud.syncIdentity)!!
            val database = Room.databaseBuilder(
                env.context,
                BudgetProfileDatabase::class.java,
                env.registryName
            ).build()
            try {
                val cleanup =
                    async(Dispatchers.IO) { AccountCloudEraser(env.context, database, env.manager).erase("owner") }
                withTimeout(5_000) { while (env.manager.active.value?.profile?.id == cloud.id) delay(10) }
                assertFalse(cleanup.isCompleted)
                assertTrue(env.context.getDatabasePath(cloud.databaseName).exists())
                lease.close()
                withTimeout(5_000) { cleanup.await() }
                assertFalse(env.context.getDatabasePath(cloud.databaseName).exists())
            } finally {
                lease.close()
                database.close()
            }
        }
    }
}
