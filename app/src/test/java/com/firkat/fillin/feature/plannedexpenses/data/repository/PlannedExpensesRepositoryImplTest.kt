package com.firkat.fillin.feature.plannedexpenses.data.repository

import com.firkat.fillin.feature.plannedexpenses.data.remote.PlannedExpensesApi
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.ClosePlannedExpenseRequestDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.CreatePlannedExpenseRuleRequestDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseCategoryDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseMoneyDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseRuleDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpensesResponseDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.UpdatePlannedExpenseRuleRequestDto
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
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
import retrofit2.Response

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
                        rule = PlannedExpenseRuleDto(id = "future-rule", version = 3),
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
        assertEquals("future-rule", result.items.last().ruleId)
        assertEquals(3L, result.items.last().ruleVersion)
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

    @Test
    fun `creates monthly rule with exact decimal amount and local date`() = runBlocking {
        val api = RecordingPlannedExpensesApi(response(emptyList()))
        val repository = PlannedExpensesRepositoryImpl(api = api, apiToken = "owner-token")

        val rule = repository.createRule(
            categoryId = "category-id",
            amount = BigDecimal("120000.00"),
            frequency = PlannedExpenseFrequency.Monthly,
            scheduleValue = 10,
            startsOn = LocalDate.of(2026, 10, 10),
        )

        assertEquals("Bearer owner-token", api.authorization)
        assertEquals(
            CreatePlannedExpenseRuleRequestDto(
                categoryId = "category-id",
                amount = "120000.00",
                frequency = "MONTHLY",
                scheduleValue = 10,
                startsOn = "2026-10-10",
            ),
            api.createRequest,
        )
        assertEquals("rule-id", rule.id)
        assertEquals(0L, rule.version)
    }

    @Test
    fun `updates amount with optimistic version`() = runBlocking {
        val api = RecordingPlannedExpensesApi(response(emptyList()))
        val repository = PlannedExpensesRepositoryImpl(api = api, apiToken = "owner-token")

        val rule = repository.updateRule(
            id = "rule-id",
            version = 4,
            amount = BigDecimal("125000.00"),
        )

        assertEquals("rule-id", api.ruleId)
        assertEquals(
            UpdatePlannedExpenseRuleRequestDto(
                version = 4,
                amount = "125000.00",
            ),
            api.updateRequest,
        )
        assertEquals(1L, rule.version)
    }

    @Test
    fun `deletes rule with version query`() = runBlocking {
        val api = RecordingPlannedExpensesApi(response(emptyList()))
        val repository = PlannedExpensesRepositoryImpl(api = api, apiToken = "owner-token")

        repository.deleteRule(id = "rule-id", version = 5)

        assertEquals("rule-id", api.ruleId)
        assertEquals(5L, api.deletedVersion)
    }

    @Test
    fun `closes occurrence with its current version and validates paid response`() = runBlocking {
        val api = RecordingPlannedExpensesApi(response(emptyList())).apply {
            closeResponse = expense(
                id = "occurrence-id",
                dueOn = "2026-10-10",
                overdue = false,
            ).copy(
                paidAmount = PlannedExpenseMoneyDto("39000.00", "KZT"),
                remainingAmount = PlannedExpenseMoneyDto("0.00", "KZT"),
                status = "PAID",
                version = 2,
            )
        }
        val repository = PlannedExpensesRepositoryImpl(api = api, apiToken = "owner-token")

        repository.closeExpense(id = "occurrence-id", version = 1)

        assertEquals("Bearer owner-token", api.authorization)
        assertEquals("occurrence-id", api.closedExpenseId)
        assertEquals(ClosePlannedExpenseRequestDto(version = 1), api.closeRequest)
    }

    @Test
    fun `rejects close response that still has a remaining amount`() {
        val api = RecordingPlannedExpensesApi(response(emptyList())).apply {
            closeResponse = expense(
                id = "occurrence-id",
                dueOn = "2026-10-10",
                overdue = false,
            ).copy(status = "PAID")
        }
        val repository = PlannedExpensesRepositoryImpl(api = api, apiToken = "owner-token")

        val exception = assertThrows(PlannedExpensesException.InvalidResponse::class.java) {
            runBlocking { repository.closeExpense(id = "occurrence-id", version = 1) }
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
        rule: PlannedExpenseRuleDto? = null,
    ) = PlannedExpenseDto(
        id = id,
        rule = rule,
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
    var createRequest: CreatePlannedExpenseRuleRequestDto? = null
    var updateRequest: UpdatePlannedExpenseRuleRequestDto? = null
    var ruleId: String? = null
    var deletedVersion: Long? = null
    var closeResponse: PlannedExpenseDto? = null
    var closedExpenseId: String? = null
    var closeRequest: ClosePlannedExpenseRequestDto? = null

    override suspend fun getPlannedExpenses(
        authorization: String,
    ): PlannedExpensesResponseDto {
        callCount += 1
        this.authorization = authorization
        return response
    }

    override suspend fun createRule(
        authorization: String,
        request: CreatePlannedExpenseRuleRequestDto,
    ): Response<PlannedExpenseRuleDto> {
        this.authorization = authorization
        createRequest = request
        return Response.success(
            201,
            PlannedExpenseRuleDto(id = "rule-id", version = 0),
        )
    }

    override suspend fun updateRule(
        authorization: String,
        ruleId: String,
        request: UpdatePlannedExpenseRuleRequestDto,
    ): Response<PlannedExpenseRuleDto> {
        this.authorization = authorization
        this.ruleId = ruleId
        updateRequest = request
        return Response.success(
            200,
            PlannedExpenseRuleDto(id = ruleId, version = 1),
        )
    }

    override suspend fun deleteRule(
        authorization: String,
        ruleId: String,
        version: Long,
    ): Response<Unit> {
        this.authorization = authorization
        this.ruleId = ruleId
        deletedVersion = version
        return Response.success(204, Unit)
    }

    override suspend fun closeExpense(
        authorization: String,
        expenseId: String,
        request: ClosePlannedExpenseRequestDto,
    ): Response<PlannedExpenseDto> {
        this.authorization = authorization
        closedExpenseId = expenseId
        closeRequest = request
        return Response.success(200, checkNotNull(closeResponse))
    }
}
