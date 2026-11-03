package com.firkat.fillin.feature.budgetmanagement

import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetmanagement.data.*
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.profile.data.RoomProfileBudgetsRepository
import kotlinx.coroutines.flow.first
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

    @Test fun localRenameAndDeletePreserveAnotherBudgetAndRequireExplicitSelection() = runBlocking {
        env.manager.restore()
        repository.createLocal("Первый", "100".toBigDecimal(), LocalDate.now())
        val first = env.manager.active.value!!.profile.id
        repository.createLocal("Второй", "200".toBigDecimal(), LocalDate.now())
        val second = env.manager.active.value!!.profile.id
        service.submit(first, "Новое имя", false)
        assertEquals("Новое имя", env.registry.find(first)?.name)
        service.submit(second, "Второй", true)
        assertNull(env.registry.find(second))
        assertTrue(env.registry.requiresSelection(null))
        env.manager.restore()
        assertNull(env.manager.active.value)
        assertEquals(first, repository.observe().first().single().id)
        repository.select(first)
        assertFalse(env.manager.selectionRequired.value)
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
        assertNull(env.manager.active.value)
    }
}
