package com.firkat.fillin.feature.plannedexpenses.data.repository

import com.firkat.fillin.feature.plannedexpenses.data.remote.PlannedExpensesApi
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseCategoryDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseMoneyDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpensesResponseDto
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PlannedExpensesRepositoryImplTest {
    @Test
    fun `maps response without losing monetary precision and sends bearer token`() = runBlocking {
        val api = RecordingPlannedExpensesApi(
            response = response(
                items = listOf(
                    expense(
                        id = "future",
                        dueOn = "2026-10-10",
                        overdue = false,
                    ),
                    expense(
                        id = "overdue",
                        dueOn = "2026-10-01",
                        overdue = true,
                    ),
                ),
            ),
        )
        val repository = PlannedExpensesRepositoryImpl(
            api = api,
            apiToken = "test-token",
        )

        val result = repository.getPlannedExpenses()

        assertEquals("Bearer test-token", api.authorization)
        assertEquals(LocalDate.of(2026, 10, 7), result.asOf)
        assertEquals(LocalDate.of(2026, 12, 31), result.throughInclusive)
        assertEquals(listOf("overdue", "future"), result.items.map { it.id })
        with(result.items.first()) {
            assertEquals(BigDecimal("120000.00"), plannedAmount)
            assertEquals(BigDecimal("20000.00"), paidAmount)
            assertEquals(BigDecimal("100000.00"), remainingAmount)
            assertEquals("KZT", currency)
            assertEquals(PlannedExpenseStatus.Scheduled, status)
            assertEquals(Instant.parse("2026-10-07T03:00:00Z"), modifiedAt)
        }
    }

    @Test
    fun `rejects missing api token before network request`() {
        val api = RecordingPlannedExpensesApi(response(emptyList()))
        val repository = PlannedExpensesRepositoryImpl(api = api, apiToken = "")

        val exception = assertThrows(PlannedExpensesException.MissingApiToken::class.java) {
            runBlocking { repository.getPlannedExpenses() }
        }

        assertSame(PlannedExpensesException.MissingApiToken, exception)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `rejects response with mismatched currencies`() {
        val item = expense(id = "expense", dueOn = "2026-10-10", overdue = false).copy(
            remainingAmount = PlannedExpenseMoneyDto("100000.00", "USD"),
        )
        val repository = PlannedExpensesRepositoryImpl(
            api = RecordingPlannedExpensesApi(response(listOf(item))),
            apiToken = "test-token",
        )

        val exception = assertThrows(PlannedExpensesException.InvalidResponse::class.java) {
            runBlocking { repository.getPlannedExpenses() }
        }

        assertSame(PlannedExpensesException.InvalidResponse, exception)
    }

    private fun response(items: List<PlannedExpenseDto>) = PlannedExpensesResponseDto(
        asOf = "2026-10-07",
        throughInclusive = "2026-12-31",
        items = items,
    )

    private fun expense(
        id: String,
        dueOn: String,
        overdue: Boolean,
    ) = PlannedExpenseDto(
        id = id,
        category = PlannedExpenseCategoryDto("category-id", "Жильё"),
        plannedAmount = PlannedExpenseMoneyDto("120000.00", "KZT"),
        paidAmount = PlannedExpenseMoneyDto("20000.00", "KZT"),
        remainingAmount = PlannedExpenseMoneyDto("100000.00", "KZT"),
        dueOn = dueOn,
        overdue = overdue,
        status = "SCHEDULED",
        version = 1,
        createdAt = "2026-10-01T04:00:00Z",
        modifiedAt = "2026-10-07T03:00:00Z",
    )
}

private class RecordingPlannedExpensesApi(
    private val response: PlannedExpensesResponseDto,
) : PlannedExpensesApi {
    var authorization: String? = null
    var callCount: Int = 0

    override suspend fun getPlannedExpenses(
        authorization: String,
    ): PlannedExpensesResponseDto {
        callCount += 1
        this.authorization = authorization
        return response
    }
}
