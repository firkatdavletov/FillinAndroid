package com.firkat.fillin.feature.budgetmanagement

import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetmanagement.data.*
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.sync.data.BudgetStore
import com.firkat.fillin.feature.sync.data.CreateCloudBudget
import kotlinx.serialization.json.Json
import com.firkat.fillin.feature.profile.data.RoomProfileBudgetsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BudgetManagementDeviceTest {
    private val env = BudgetConnectionDeviceTest.Environment()
    private val service = BudgetManagement(env.context, env.registryDb, env.registry, env.manager, env.auth, env.requests, env.uploads, env.koin.get(), enqueue = {})
    private val repository = RoomProfileBudgetsRepository(env.auth, env.registry, env.manager, BudgetDatabaseFactory(env.context), env.connection, env.koin.get(), env.uploads, management = service)
    @After fun close() = env.close()

    @Test fun localRenameAndDeleteRestoreAnotherBudget() = runBlocking {
        env.manager.restore()
        repository.createLocal("Первый", "100".toBigDecimal(), LocalDate.now())
        val first = env.manager.active.value!!.profile.id
        repository.createLocal("Второй", "200".toBigDecimal(), LocalDate.now())
        val second = env.manager.active.value!!.profile.id
        service.submit(first, "Новое имя", false)
        assertEquals("Новое имя", env.registry.find(first)?.name)
        service.submit(second, "Второй", true)
        assertNull(env.registry.find(second))
        assertEquals(first, env.manager.active.value?.profile?.id)
        assertEquals(first, repository.observe().first().single().id)
        assertFalse(env.manager.selectionRequired.value)
    }

    @Test fun concurrentLocalCreationSharesTwoSlotsAndDeletionReleasesOne() = runBlocking {
        val results = kotlinx.coroutines.coroutineScope {
            (1..3).map { index ->
                async {
                    runCatching {
                        repository.createLocal("Бюджет $index", "0".toBigDecimal(), LocalDate.now())
                    }
                }
            }.awaitAll()
        }
        assertEquals(2, results.count { it.isSuccess })
        val failure = results.single { it.isFailure }.exceptionOrNull()
        assertEquals("local_budget_limit", (failure as? com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure)?.code)
        val removed = env.manager.active.value!!.profile
        service.submit(removed.id, requireNotNull(removed.name), true)
        repository.createLocal("После удаления", "0".toBigDecimal(), LocalDate.now())
        assertEquals(2, repository.observe().first().size)
    }

    @Test fun restartRecoversCommittedReservationAndReleasesIncompleteOne() = runBlocking {
        val factory = BudgetDatabaseFactory(env.context)
        val committed = env.registry.reserveLocal("Готовый", factory)
        val interrupted = env.registry.reserveLocal("Прерванный", factory)
        val database = factory.open(committed)
        try {
            val session = com.firkat.fillin.feature.sync.data.CloudBudgetSession(
                database, com.firkat.fillin.feature.sync.data.LocalOnlyCloudApi, "", env.koin.get(),
                com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler {},
            )
            session.createLocal("123.45".toBigDecimal(), session.today)
        } finally {
            database.close()
        }

        val reopened = com.firkat.fillin.feature.budgetprofile.data.RoomBudgetProfileRegistry(env.registryDb, env.backend)
        reopened.recoverLocalCreations(factory)
        assertTrue(reopened.isReady(committed, factory))
        assertNull(reopened.find(interrupted.id))
        assertEquals(1, reopened.observeLocalCount().first())
        assertEquals(committed.id, reopened.available(null).first { it.name == "Готовый" }.id)
    }

    @Test fun offlineCurrencyChangeSurvivesRetryAndDoesNotChangeOpeningBalance() = runBlocking {
        env.auth.login("owner")
        env.manager.restore()
        val remote = env.connection.budgets("owner").single()
        env.connection.connect("owner", remote)
        val lease = requireNotNull(env.manager.active.value?.retain())
        lease.use {
            val store = it.scope.get<BudgetStore>()
            val asOf = java.time.Instant.now()
            val before = store.snapshot().dashboardInput(asOf)
            assertEquals("KZT", before.currency)
            service.submit("cloud:${remote.id}", remote.name, false, BudgetCurrency.USD, rename = false)
            assertEquals(BudgetCurrency.USD, store.currency.first())
            val entry = env.registryDb.management().all().single()
            env.server.loseManagementResponse = true
            assertFalse(service.run())
            assertEquals(entry.requestId, env.registryDb.management().all().single().requestId)
            assertTrue(service.run())
            assertEquals(listOf(entry.requestId, entry.requestId), env.server.managementRequests)
            assertEquals(BudgetCurrency.USD, store.currency.first())
            val after = store.snapshot().dashboardInput(asOf)
            assertEquals(before.copy(currency = "USD"), after)
        }
    }

    @Test fun localChangePersistsPerBudgetAndUpdatesImportBootstrap() = runBlocking {
        env.manager.restore()
        repository.createLocal("Первый", "100.25".toBigDecimal(), LocalDate.now())
        val first = env.manager.active.value!!.profile
        repository.createLocal("Второй", "200.50".toBigDecimal(), LocalDate.now())
        val second = env.manager.active.value!!.profile
        service.submit(first.id, "Первый", false, BudgetCurrency.EUR, rename = false)
        repository.select(first.id)
        val lease = requireNotNull(env.manager.active.value?.retain())
        lease.use {
            val state = requireNotNull(it.scope.get<OperationDatabase>().operations().state())
            assertEquals("EUR", state.currency)
            val bootstrap = env.koin.get<Json>().decodeFromString<CreateCloudBudget>(requireNotNull(state.bootstrapPayload))
            assertEquals("EUR", bootstrap.currency)
            assertEquals("100.25", bootstrap.openingBalance)
        }
        env.database(second) { assertEquals("KZT", it.operations().state()?.currency) }
    }

    @Test fun lostResponsesReuseRequestIdsAndQueueWaitsForOriginalAccount() = runBlocking {
        env.auth.login("owner")
        env.manager.restore()
        val remote = env.connection.budgets("owner").single()
        env.connection.connect("owner", remote)
        service.submit("cloud:${remote.id}", "Новое имя", false)
        val renameId = env.registryDb.management().all().single().requestId
        env.server.loseManagementResponse = true
        assertFalse(service.run())
        assertEquals(renameId, env.registryDb.management().all().single().requestId)
        env.auth.login("other")
        assertTrue(service.run())
        assertEquals(1, env.server.managementRequests.size)
        env.auth.login("owner")
        assertTrue(service.run())
        assertEquals(listOf(renameId, renameId), env.server.managementRequests)
        assertEquals("Новое имя", env.connection.cachedBudgets("owner").single().name)
        service.submit("cloud:${remote.id}", "Новое имя", true)
        val deleteId = env.registryDb.management().all().single().requestId
        env.server.loseManagementResponse = true
        assertFalse(service.run())
        assertTrue(service.run())
        assertEquals(listOf(deleteId, deleteId), env.server.managementRequests.takeLast(2))
        assertTrue(env.registryDb.management().all().isEmpty())
    }

    @Test fun queuedDeletionSurvivesReopeningAndCatalogOverlayHidesIt() = runBlocking {
        env.auth.login("owner")
        env.manager.restore()
        val remote = env.connection.budgets("owner").single()
        env.connection.connect("owner", remote)
        val id = env.manager.active.value!!.profile.id
        service.submit("cloud:${remote.id}", remote.name, true)
        assertTrue(env.registryDb.management().all().single().deleting)
        assertTrue(env.connection.cachedBudgets("owner").isEmpty())
        assertTrue(env.registry.available("owner").none { it.id == id })
        env.manager.restore()
        assertNull(env.manager.active.value?.profile?.budgetId)
    }
}
