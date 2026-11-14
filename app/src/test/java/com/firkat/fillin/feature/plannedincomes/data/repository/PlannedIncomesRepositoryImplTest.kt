package com.firkat.fillin.feature.plannedincomes.data.repository

import com.firkat.fillin.feature.plannedincomes.data.remote.PlannedIncomesApi
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.CreatePlannedIncomeRequestDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeCategoryDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeMoneyDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomesResponseDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.UpdatePlannedIncomeRequestDto
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
import retrofit2.Response

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
                        overdue = false
                    ),
                    income(
                        id = "overdue",
                        amount = "12345678901234567890.12",
                        dueOn = "2026-10-01",
                        overdue = true
                    )
                )
            )
        )
        val repository = PlannedIncomesRepositoryImpl(
            api = api,
            apiToken = "test-token"
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
                            overdue = false
                        )
                    )
                )
            ),
            apiToken = "test-token"
        )

        val exception = assertThrows(PlannedIncomesException.InvalidResponse::class.java) {
            runBlocking { repository.getPlannedIncomes() }
        }

        assertSame(PlannedIncomesException.InvalidResponse, exception)
    }

    @Test
    fun `creates planned income with exact amount and local date`() = runBlocking {
        val api = RecordingPlannedIncomesApi(response(emptyList()))
        val repository = PlannedIncomesRepositoryImpl(api = api, apiToken = "owner-token")

        val result = repository.createPlannedIncome(
            categoryId = "category-id",
            amount = BigDecimal("1300000.00"),
            dueOn = LocalDate.of(2026, 10, 10)
        )

        assertEquals("Bearer owner-token", api.authorization)
        assertEquals(
            CreatePlannedIncomeRequestDto(
                categoryId = "category-id",
                amount = "1300000.00",
                dueOn = "2026-10-10"
            ),
            api.createRequest
        )
        assertEquals("created-income", result.id)
        assertEquals(0L, result.version)
    }

    @Test
    fun `updates only supplied fields with optimistic version`() = runBlocking {
        val api = RecordingPlannedIncomesApi(response(emptyList()))
        val repository = PlannedIncomesRepositoryImpl(api = api, apiToken = "owner-token")

        val result = repository.updatePlannedIncome(
            id = "income-id",
            version = 4,
            categoryId = null,
            amount = BigDecimal("1350000.00"),
            dueOn = LocalDate.of(2026, 10, 12)
        )

        assertEquals("income-id", api.incomeId)
        assertEquals(
            UpdatePlannedIncomeRequestDto(
                version = 4,
                amount = "1350000.00",
                dueOn = "2026-10-12"
            ),
            api.updateRequest
        )
        assertEquals(5L, result.version)
    }

    @Test
    fun `deletes planned income with current version`() = runBlocking {
        val api = RecordingPlannedIncomesApi(response(emptyList()))
        val repository = PlannedIncomesRepositoryImpl(api = api, apiToken = "owner-token")

        repository.deletePlannedIncome(id = "income-id", version = 6)

        assertEquals("income-id", api.incomeId)
        assertEquals(6L, api.deletedVersion)
    }

    private fun response(
        items: List<PlannedIncomeDto>
    ) = PlannedIncomesResponseDto(
        asOf = "2026-10-07",
        items = items
    )

    private fun income(
        id: String,
        amount: String,
        dueOn: String,
        overdue: Boolean
    ) = PlannedIncomeDto(
        id = id,
        category = PlannedIncomeCategoryDto("category-id", "Зарплата"),
        plannedAmount = PlannedIncomeMoneyDto(amount, "KZT"),
        dueOn = dueOn,
        overdue = overdue,
        status = "SCHEDULED",
        version = 0,
        createdAt = "2026-10-01T04:00:00Z",
        modifiedAt = "2026-10-01T04:00:00Z"
    )
}

private class RecordingPlannedIncomesApi(private val response: PlannedIncomesResponseDto) : PlannedIncomesApi {
    var authorization: String? = null
    var callCount: Int = 0
    var createRequest: CreatePlannedIncomeRequestDto? = null
    var updateRequest: UpdatePlannedIncomeRequestDto? = null
    var incomeId: String? = null
    var deletedVersion: Long? = null

    override suspend fun getPlannedIncomes(
        authorization: String
    ): PlannedIncomesResponseDto {
        callCount += 1
        this.authorization = authorization
        return response
    }

    override suspend fun createPlannedIncome(
        authorization: String,
        request: CreatePlannedIncomeRequestDto
    ): Response<PlannedIncomeDto> {
        this.authorization = authorization
        createRequest = request
        return Response.success(
            201,
            plannedIncomeDto(
                id = "created-income",
                amount = request.amount,
                dueOn = request.dueOn,
                version = 0
            )
        )
    }

    override suspend fun updatePlannedIncome(
        authorization: String,
        incomeId: String,
        request: UpdatePlannedIncomeRequestDto
    ): Response<PlannedIncomeDto> {
        this.authorization = authorization
        this.incomeId = incomeId
        updateRequest = request
        return Response.success(
            200,
            plannedIncomeDto(
                id = incomeId,
                amount = request.amount ?: "1300000.00",
                dueOn = request.dueOn ?: "2026-10-10",
                version = request.version + 1
            )
        )
    }

    override suspend fun deletePlannedIncome(
        authorization: String,
        incomeId: String,
        version: Long
    ): Response<Unit> {
        this.authorization = authorization
        this.incomeId = incomeId
        deletedVersion = version
        return Response.success(204, Unit)
    }
}

private fun plannedIncomeDto(
    id: String,
    amount: String,
    dueOn: String,
    version: Long
) = PlannedIncomeDto(
    id = id,
    category = PlannedIncomeCategoryDto("category-id", "Зарплата"),
    plannedAmount = PlannedIncomeMoneyDto(amount, "KZT"),
    dueOn = dueOn,
    overdue = false,
    status = "SCHEDULED",
    version = version,
    createdAt = "2026-10-01T04:00:00Z",
    modifiedAt = "2026-10-01T04:00:00Z"
)
