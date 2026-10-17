package com.firkat.fillin.feature.plannedoperations.data.repository

import com.firkat.fillin.feature.plannedoperations.data.remote.PlannedOperationsApi
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperation
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationPeriod
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class PlannedOperationsHttpTest {
    @Test
    fun `default request omits type and custom dates and maps both types with rule versions`() = runBlocking {
        val requests = mutableListOf<Request>()
        val result = repository(requests, response(EXPENSE, INCOME)).getPlannedOperations(PlannedOperationsFilter())

        val request = requests.single()
        assertEquals("/api/v1/planned-operations", request.url.encodedPath)
        assertEquals("Bearer synthetic-test-token", request.header("Authorization"))
        assertEquals("END_OF_MONTH", request.url.queryParameter("period"))
        assertNull(request.url.queryParameter("type"))
        assertNull(request.url.queryParameter("dateFrom"))
        assertNull(request.url.queryParameter("dateTo"))
        val expense = (result.items[0] as PlannedOperation.Expense).expense
        assertEquals(BigDecimal("60.00"), expense.remainingAmount)
        assertEquals(BigDecimal("40.00"), expense.paidAmount)
        assertEquals("rule-id", expense.ruleId)
        assertEquals(3L, expense.ruleVersion)
        assertEquals("income", result.items[1].id)
    }

    @Test
    fun `passes combined income and custom filters with inclusive same day bounds`() = runBlocking {
        val requests = mutableListOf<Request>()
        val day = LocalDate.of(2026, 10, 31)
        val filter = PlannedOperationsFilter(PlannedOperationType.Income, PlannedOperationPeriod.Custom, day, day)
        repository(requests, response(INCOME, from = day.toString())).getPlannedOperations(filter)

        val request = requests.single()
        assertEquals("INCOME", request.url.queryParameter("type"))
        assertEquals("CUSTOM", request.url.queryParameter("period"))
        assertEquals(day.toString(), request.url.queryParameter("dateFrom"))
        assertEquals(day.toString(), request.url.queryParameter("dateTo"))
    }

    @Test
    fun `year preset is sent without custom dates`() = runBlocking {
        val requests = mutableListOf<Request>()
        repository(requests, response()).getPlannedOperations(
            PlannedOperationsFilter(PlannedOperationType.Expense, PlannedOperationPeriod.EndOfYear),
        )
        assertEquals("END_OF_YEAR", requests.single().url.queryParameter("period"))
        assertEquals("EXPENSE", requests.single().url.queryParameter("type"))
        assertNull(requests.single().url.queryParameter("dateFrom"))
    }

    @Test
    fun `maps access validation and server errors without exposing their payload`() {
        mapOf(
            400 to PlannedOperationsException.InvalidRequest,
            401 to PlannedOperationsException.Unauthorized,
            403 to PlannedOperationsException.Forbidden,
            500 to PlannedOperationsException.Unknown,
        ).forEach { (status, expected) ->
            val repository = repository(mutableListOf(), "{}", status)
            assertEquals(expected, runCatching {
                runBlocking { repository.getPlannedOperations(PlannedOperationsFilter()) }
            }.exceptionOrNull())
        }
    }

    @Test
    fun `rejects unsupported types broken amounts missing rules and out of range dates`() {
        listOf(
            response(EXPENSE.replace("EXPENSE", "EXPENSE_REFUND")),
            response(EXPENSE.replace("100.00", "100.001")),
            response(EXPENSE.replace("\"rule\": {\"id\":\"rule-id\",\"version\":3},", "")),
            response(INCOME.replace("2026-10-31", "2026-11-01")),
            response(INCOME.replace("\"amount\":\"0.00\"", "\"amount\":\"10.00\"")),
        ).forEach { body ->
            assertThrows(PlannedOperationsException.InvalidResponse::class.java) {
                runBlocking { repository(mutableListOf(), body).getPlannedOperations(PlannedOperationsFilter()) }
            }
        }
    }

    @Test
    fun `missing token prevents even a synthetic HTTP request`() {
        val requests = mutableListOf<Request>()
        assertThrows(PlannedOperationsException.MissingApiToken::class.java) {
            runBlocking {
                repository(requests, response(), token = "").getPlannedOperations(PlannedOperationsFilter())
            }
        }
        assertEquals(0, requests.size)
    }

    private fun repository(
        requests: MutableList<Request>,
        body: String,
        code: Int = 200,
        token: String = "synthetic-test-token",
    ): PlannedOperationsRepositoryImpl {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Synthetic response")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://synthetic.invalid/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build().create(PlannedOperationsApi::class.java)
        return PlannedOperationsRepositoryImpl(api, token)
    }
}

private fun response(vararg items: String, from: String = "2026-10-17") = """
    {"asOf":"2026-10-17","dateFrom":"$from","dateTo":"2026-10-31","items":[${items.joinToString(",")}]}
""".trimIndent()

private val EXPENSE = """
    {"id":"expense","type":"EXPENSE","category":{"id":"rent","name":"Тестовый расход"},
     "plannedAmount":{"amount":"100.00","currency":"KZT"},
     "paidAmount":{"amount":"40.00","currency":"KZT"},
     "remainingAmount":{"amount":"60.00","currency":"KZT"},
     "rule": {"id":"rule-id","version":3},
     "dueOn":"2026-10-18","overdue":false,"status":"SCHEDULED","version":1,
     "createdAt":"2026-10-01T00:00:00Z","modifiedAt":"2026-10-17T00:00:00Z"}
""".trimIndent()

private val INCOME = """
    {"id":"income","type":"INCOME","category":{"id":"salary","name":"Тестовый доход"},
     "plannedAmount":{"amount":"200.00","currency":"KZT"},
     "paidAmount":{"amount":"0.00","currency":"KZT"},
     "remainingAmount":{"amount":"200.00","currency":"KZT"},"rule":null,
     "dueOn":"2026-10-31","overdue":false,"status":"SCHEDULED","version":0,
     "createdAt":"2026-10-01T00:00:00Z","modifiedAt":"2026-10-01T00:00:00Z"}
""".trimIndent()
