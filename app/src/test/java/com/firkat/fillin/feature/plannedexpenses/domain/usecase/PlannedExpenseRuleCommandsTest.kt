package com.firkat.fillin.feature.plannedexpenses.domain.usecase

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PlannedExpenseRuleCommandsTest {
    @Test
    fun `monthly schedule accepts day 31`() = runBlocking {
        val repository = RecordingPlannedExpensesRepository()

        CreatePlannedExpenseRule(repository)(
            categoryId = "category-id",
            amount = BigDecimal("120000.00"),
            frequency = PlannedExpenseFrequency.Monthly,
            scheduleValue = 31,
            startsOn = LocalDate.of(2026, 10, 10),
        )

        assertEquals(31, repository.createdScheduleValue)
        assertEquals(BigDecimal("120000.00"), repository.createdAmount)
    }

    @Test
    fun `weekly schedule rejects values outside ISO weekday range`() {
        val repository = RecordingPlannedExpensesRepository()

        val exception = assertThrows(PlannedExpensesException.InvalidRequest::class.java) {
            runBlocking {
                CreatePlannedExpenseRule(repository)(
                    categoryId = "category-id",
                    amount = BigDecimal("100.00"),
                    frequency = PlannedExpenseFrequency.Weekly,
                    scheduleValue = 8,
                    startsOn = LocalDate.of(2026, 10, 10),
                )
            }
        }

        assertSame(PlannedExpensesException.InvalidRequest, exception)
    }

    @Test
    fun `amount rejects more than two fraction digits`() {
        val repository = RecordingPlannedExpensesRepository()

        val exception = assertThrows(PlannedExpensesException.InvalidRequest::class.java) {
            runBlocking {
                UpdatePlannedExpenseRule(repository)(
                    expense = expense(),
                    amount = BigDecimal("1.001"),
                )
            }
        }

        assertSame(PlannedExpensesException.InvalidRequest, exception)
    }

    @Test
    fun `update and delete forward rule id and version from expense`() = runBlocking {
        val repository = RecordingPlannedExpensesRepository()
        val expense = expense()

        UpdatePlannedExpenseRule(repository)(expense, BigDecimal("125000.00"))
        DeletePlannedExpenseRule(repository)(expense)

        assertEquals("rule-id", repository.ruleId)
        assertEquals(7L, repository.ruleVersion)
        assertEquals(BigDecimal("125000.00"), repository.updatedAmount)
    }

    @Test
    fun `close forwards occurrence id and occurrence version`() = runBlocking {
        val repository = RecordingPlannedExpensesRepository()
        val expense = expense().copy(
            paidAmount = BigDecimal("39000.00"),
            remainingAmount = BigDecimal("81000.00"),
        )

        ClosePlannedExpense(repository)(expense)

        assertEquals("occurrence-id", repository.closedExpenseId)
        assertEquals(7L, repository.closedExpenseVersion)
    }

    @Test
    fun `close rejects occurrence without a payment`() {
        val repository = RecordingPlannedExpensesRepository()

        val exception = assertThrows(PlannedExpensesException.InvalidRequest::class.java) {
            runBlocking { ClosePlannedExpense(repository)(expense()) }
        }

        assertSame(PlannedExpensesException.InvalidRequest, exception)
        assertEquals(null, repository.closedExpenseId)
    }

    private fun expense() = PlannedExpense(
        id = "occurrence-id",
        categoryId = "category-id",
        categoryName = "Жильё",
        plannedAmount = BigDecimal("120000.00"),
        paidAmount = BigDecimal.ZERO,
        remainingAmount = BigDecimal("120000.00"),
        currency = "KZT",
        dueOn = LocalDate.of(2026, 10, 10),
        overdue = false,
        status = PlannedExpenseStatus.Scheduled,
        version = 7,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        modifiedAt = Instant.parse("2026-10-01T00:00:00Z"),
        ruleId = "rule-id",
        ruleVersion = 7,
    )
}

private class RecordingPlannedExpensesRepository : PlannedExpensesRepository {
    var createdAmount: BigDecimal? = null
    var createdScheduleValue: Int? = null
    var updatedAmount: BigDecimal? = null
    var ruleId: String? = null
    var ruleVersion: Long? = null
    var closedExpenseId: String? = null
    var closedExpenseVersion: Long? = null

    override suspend fun getPlannedExpenses(): PlannedExpenses = PlannedExpenses(
        asOf = LocalDate.of(2026, 10, 8),
        throughInclusive = LocalDate.of(2026, 12, 31),
        items = emptyList(),
    )

    override suspend fun createRule(
        categoryId: String,
        amount: BigDecimal,
        frequency: PlannedExpenseFrequency,
        scheduleValue: Int,
        startsOn: LocalDate,
    ): PlannedExpenseRule {
        createdAmount = amount
        createdScheduleValue = scheduleValue
        return PlannedExpenseRule(id = "rule-id", version = 0)
    }

    override suspend fun updateRule(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): PlannedExpenseRule {
        ruleId = id
        ruleVersion = version
        updatedAmount = amount
        return PlannedExpenseRule(id = id, version = version + 1)
    }

    override suspend fun deleteRule(id: String, version: Long) {
        ruleId = id
        ruleVersion = version
    }

    override suspend fun closeExpense(id: String, version: Long) {
        closedExpenseId = id
        closedExpenseVersion = version
    }
}
