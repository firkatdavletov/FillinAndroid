package com.firkat.fillin.feature.plannedincomes.data.repository

import com.firkat.fillin.feature.plannedincomes.data.remote.PlannedIncomesApi
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeCategoryDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeMoneyDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomesResponseDto
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PlannedIncomesRepositoryImplTest {
    @Test
    fun `maps response in due date order without losing monetary precision`() = runBlocking {
        val api = RecordingPlannedIncomesApi(
            response = response(
                items = listOf(
                    income(
                        id = "future",
                        amount = "1300000.00",
                        dueOn = "2026-10-10",
                        overdue = false,
                    ),
                    income(
                        id = "overdue",
                        amount = "12345678901234567890.12",
                        dueOn = "2026-10-01",
                        overdue = true,
                    ),
                ),
            ),
        )
        val repository = PlannedIncomesRepositoryImpl(
            api = api,
            apiToken = "test-token",
        )

        val result = repository.getPlannedIncomes()

        assertEquals("Bearer test-token", api.authorization)
        assertEquals(LocalDate.of(2026, 10, 7), result.asOf)
        assertEquals(listOf("overdue", "future"), result.items.map { it.id })
        with(result.items.first()) {
            assertEquals(BigDecimal("12345678901234567890.12"), plannedAmount)
            assertEquals("KZT", currency)
            assertEquals(PlannedIncomeStatus.Scheduled, status)
            assertEquals(Instant.parse("2026-10-01T04:00:00Z"), modifiedAt)
        }
    }

    @Test
    fun `rejects missing api token before network request`() {
        val api = RecordingPlannedIncomesApi(response(emptyList()))
        val repository = PlannedIncomesRepositoryImpl(api = api, apiToken = "")

        val exception = assertThrows(PlannedIncomesException.MissingApiToken::class.java) {
            runBlocking { repository.getPlannedIncomes() }
        }

        assertSame(PlannedIncomesException.MissingApiToken, exception)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `rejects amount that is not a decimal string`() {
        val repository = PlannedIncomesRepositoryImpl(
            api = RecordingPlannedIncomesApi(
                response(
                    listOf(
                        income(
                            id = "income",
                            amount = "not-a-number",
                            dueOn = "2026-10-10",
                            overdue = false,
                        ),
                    ),
                ),
            ),
            apiToken = "test-token",
        )

        val exception = assertThrows(PlannedIncomesException.InvalidResponse::class.java) {
            runBlocking { repository.getPlannedIncomes() }
        }

        assertSame(PlannedIncomesException.InvalidResponse, exception)
    }

    private fun response(items: List<PlannedIncomeDto>) = PlannedIncomesResponseDto(
        asOf = "2026-10-07",
        items = items,
    )

    private fun income(
        id: String,
        amount: String,
        dueOn: String,
        overdue: Boolean,
    ) = PlannedIncomeDto(
        id = id,
        category = PlannedIncomeCategoryDto("category-id", "Зарплата"),
        plannedAmount = PlannedIncomeMoneyDto(amount, "KZT"),
        dueOn = dueOn,
        overdue = overdue,
        status = "SCHEDULED",
        version = 0,
        createdAt = "2026-10-01T04:00:00Z",
        modifiedAt = "2026-10-01T04:00:00Z",
    )
}

private class RecordingPlannedIncomesApi(
    private val response: PlannedIncomesResponseDto,
) : PlannedIncomesApi {
    var authorization: String? = null
    var callCount: Int = 0

    override suspend fun getPlannedIncomes(
        authorization: String,
    ): PlannedIncomesResponseDto {
        callCount += 1
        this.authorization = authorization
        return response
    }
}
