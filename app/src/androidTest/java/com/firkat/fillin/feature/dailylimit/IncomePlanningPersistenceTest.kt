package com.firkat.fillin.feature.dailylimit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.plannedincomes.data.repository.OfflinePlannedIncomesRepository
import com.firkat.fillin.feature.sync.data.BudgetStore
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomePlanningPersistenceTest {
    @Test fun createsEditsDisablesAndDeletesRegularIncomeOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OperationDatabase::class.java).build()
        try {
            val category = UUID.randomUUID().toString()
            db.operations().putState(
                OperationSyncState(
                    scopeKey = "scope",
                    householdId = UUID.randomUUID().toString(),
                    memberId = UUID.randomUUID().toString(),
                    accountingStartedAt = "2026-01-01T00:00:00Z",
                    localReady = true,
                    role = "OWNER",
                    protocolVersion = 4
                )
            )
            db.operations().putCategory(LocalCategory("scope", category, "Salary", "INCOME", "INCOME", "", false, 1))
            val clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Almaty"))
            val store = BudgetStore(db, OperationSyncScheduler {}, Json, clock)
            val repo = OfflinePlannedIncomesRepository(store)
            val first = repo.saveDetails(
                null,
                category,
                "300000.00".toBigDecimal(),
                LocalDate.of(2026, 11, 15),
                "Salary",
                IncomeFrequency.MONTHLY,
                true
            )
            assertNotNull(first.ruleId)
            assertTrue(repo.getPlannedIncomes().items.any { it.dueOn == LocalDate.of(2026, 12, 15) })
            repo.saveDetails(
                first,
                category,
                "400000.00".toBigDecimal(),
                first.dueOn,
                "Updated salary",
                IncomeFrequency.MONTHLY,
                false
            )
            val projection = store.snapshot()
            assertEquals(false, projection.incomeRules.values.single().incomeActive)
            assertTrue(projection.incomes.values.all { it.incomeActive == false })
            val edited = repo.getPlannedIncomes().items.first()
            repo.deletePlannedIncome(edited.id, edited.version)
            assertTrue(repo.getPlannedIncomes().items.isEmpty())
            assertEquals(
                listOf("INCOME_RULE", "INCOME_RULE", "INCOME_RULE"),
                db.budget().commands("scope").map {
                    it.kind
                }
            )
        } finally {
            db.close()
        }
    }
}
