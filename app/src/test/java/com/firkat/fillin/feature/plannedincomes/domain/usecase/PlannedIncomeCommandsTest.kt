package com.firkat.fillin.feature.plannedincomes.domain.usecase

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PlannedIncomeCommandsTest {
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun `create accepts today and preserves decimal scale`() = runBlocking {
        val repository = RecordingPlannedIncomesRepository()

        CreatePlannedIncome(repository) { today }(
            categoryId = "category-id",
            amount = BigDecimal("1300000.00"),
            dueOn = today,
        )

        assertEquals(BigDecimal("1300000.00"), repository.amount)
        assertEquals(today, repository.dueOn)
    }

    @Test
    fun `create rejects past date`() {
        val exception = assertThrows(PlannedIncomesException.InvalidRequest::class.java) {
            runBlocking {
                CreatePlannedIncome(RecordingPlannedIncomesRepository()) { today }(
                    categoryId = "category-id",
                    amount = BigDecimal("100.00"),
                    dueOn = today.minusDays(1),
                )
            }
        }

        assertSame(PlannedIncomesException.InvalidRequest, exception)
    }

    @Test
    fun `update leaves overdue date untouched when changing amount`() = runBlocking {
        val repository = RecordingPlannedIncomesRepository()
        val income = income(dueOn = today.minusDays(3))

        UpdatePlannedIncome(repository) { today }(
            income = income,
            amount = BigDecimal("1350000.00"),
        )

        assertEquals("income-id", repository.id)
        assertEquals(2L, repository.version)
        assertEquals(BigDecimal("1350000.00"), repository.amount)
        assertNull(repository.dueOn)
    }

    @Test
    fun `update rejects a newly supplied past date`() {
        val exception = assertThrows(PlannedIncomesException.InvalidRequest::class.java) {
            runBlocking {
                UpdatePlannedIncome(RecordingPlannedIncomesRepository()) { today }(
                    income = income(dueOn = today.plusDays(1)),
                    dueOn = today.minusDays(1),
                )
            }
        }

        assertSame(PlannedIncomesException.InvalidRequest, exception)
    }

    @Test
    fun `update rejects request without changed fields`() {
        val exception = assertThrows(PlannedIncomesException.InvalidRequest::class.java) {
            runBlocking {
                UpdatePlannedIncome(RecordingPlannedIncomesRepository()) { today }(
                    income = income(dueOn = today.plusDays(1)),
                )
            }
        }

        assertSame(PlannedIncomesException.InvalidRequest, exception)
    }

    @Test
    fun `delete forwards id and version`() = runBlocking {
        val repository = RecordingPlannedIncomesRepository()

        DeletePlannedIncome(repository)(income(dueOn = today))

        assertEquals("income-id", repository.id)
        assertEquals(2L, repository.version)
    }

    private fun income(dueOn: LocalDate) = PlannedIncome(
        id = "income-id",
        categoryId = "category-id",
        categoryName = "Зарплата",
        plannedAmount = BigDecimal("1300000.00"),
        currency = "KZT",
        dueOn = dueOn,
        overdue = dueOn.isBefore(today),
        status = PlannedIncomeStatus.Scheduled,
        version = 2,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        modifiedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )
}

private class RecordingPlannedIncomesRepository : PlannedIncomesRepository {
    var id: String? = null
    var version: Long? = null
    var categoryId: String? = null
    var amount: BigDecimal? = null
    var dueOn: LocalDate? = null

    override suspend fun getPlannedIncomes(): PlannedIncomes = PlannedIncomes(
        asOf = LocalDate.of(2026, 10, 9),
        items = emptyList(),
    )

    override suspend fun createPlannedIncome(
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate,
    ): PlannedIncome {
        this.categoryId = categoryId
        this.amount = amount
        this.dueOn = dueOn
        return resultIncome(version = 0)
    }

    override suspend fun updatePlannedIncome(
        id: String,
        version: Long,
        categoryId: String?,
        amount: BigDecimal?,
        dueOn: LocalDate?,
    ): PlannedIncome {
        this.id = id
        this.version = version
        this.categoryId = categoryId
        this.amount = amount
        this.dueOn = dueOn
        return resultIncome(version = version + 1)
    }

    override suspend fun deletePlannedIncome(id: String, version: Long) {
        this.id = id
        this.version = version
    }

    private fun resultIncome(version: Long) = PlannedIncome(
        id = "income-id",
        categoryId = "category-id",
        categoryName = "Зарплата",
        plannedAmount = amount ?: BigDecimal("1300000.00"),
        currency = "KZT",
        dueOn = dueOn ?: LocalDate.of(2026, 10, 10),
        overdue = false,
        status = PlannedIncomeStatus.Scheduled,
        version = version,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        modifiedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )
}
