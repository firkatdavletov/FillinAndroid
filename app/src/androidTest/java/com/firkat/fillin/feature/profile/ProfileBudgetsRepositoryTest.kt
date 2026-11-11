package com.firkat.fillin.feature.profile

import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.profile.data.RoomProfileBudgetsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ProfileBudgetsRepositoryTest {
    private val env = BudgetConnectionDeviceTest.Environment()
    private val repository = RoomProfileBudgetsRepository(env.auth, env.registry, env.manager, BudgetDatabaseFactory(env.context), env.connection, env.koin.get(), env.uploads)

    @After fun cleanup() = env.close()

    @Test fun localBudgetsAreCreatedBeforeSelectionAndCanBeSwitchedOffline() = runBlocking {
        env.manager.restore()
        repository.createLocal("120.50".toBigDecimal(), LocalDate.now().minusDays(1))
        val first = repository.observe().first().single()
        assertTrue(first.selected)
        assertFalse(first.family)
        assertTrue(env.manager.active.value!!.scope.get<com.firkat.fillin.feature.sync.data.CloudBudgetSession>().ready.first())
        repository.createLocal("0.00".toBigDecimal(), LocalDate.now())
        assertEquals(2, repository.observe().first().size)
        repository.select(first.id)
        assertEquals(first.id, repository.observe().first().single { it.selected }.id)
        assertTrue(env.server.creations.isEmpty())
    }

    @Test fun namedLocalBudgetIsStoredAndShownInTheProfile() = runBlocking {
        env.manager.restore()
        repository.createLocal("Поездка", "25.50".toBigDecimal(), LocalDate.now().minusDays(1), com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency.EUR)
        val budget = repository.observe().first().single()
        assertEquals("Поездка", budget.name)
        assertEquals("Поездка", env.registry.find(budget.id)?.name)
        env.database(env.manager.active.value!!.profile) { assertEquals("EUR", it.operations().state()?.currency) }
        env.manager.restore()
        assertEquals("Поездка", repository.observe().first().single().name)
        assertTrue(env.server.creations.isEmpty())
    }

    @Test fun uploadedBudgetStaysVisibleBeforeTheFirstServerSync() = runBlocking {
        env.manager.restore()
        repository.createLocal("0.00".toBigDecimal(), LocalDate.now())
        val id = env.manager.active.value!!.profile.id
        env.auth.login("owner")
        env.manager.restore()
        repository.select(id)
        env.uploadConnection.connect("owner", id, "Семейный бюджет")
        val uploaded = repository.observe().first().single { it.selected }
        assertEquals("Семейный бюджет", uploaded.name)
        assertTrue(uploaded.family)
        assertTrue(env.server.creations.isEmpty())
    }
}
