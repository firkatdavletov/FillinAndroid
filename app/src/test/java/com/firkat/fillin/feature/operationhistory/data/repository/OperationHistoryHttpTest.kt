package com.firkat.fillin.feature.operationhistory.data.repository

import com.firkat.fillin.feature.operationhistory.data.remote.OperationHistoryApi
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
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

class OperationHistoryHttpTest {
    @Test
    fun `requests operations with all filters and maps income`() = runBlocking {
        val requests = mutableListOf<Request>()
        val repository = repository(requests, response("INCOME"))
        val filter = FILTER.copy(type = OperationType.Income, categoryId = "salary-id")

        val page = repository.getOperations(20, 123, filter)

        val request = requests.single()
        assertEquals("/api/v1/operations", request.url.encodedPath)
        assertEquals("Bearer synthetic-test-token", request.header("Authorization"))
        assertEquals("20", request.url.queryParameter("limit"))
        assertEquals("123", request.url.queryParameter("cursor"))
        assertEquals("INCOME", request.url.queryParameter("type"))
        assertEquals("salary-id", request.url.queryParameter("categoryId"))
        assertEquals("2026-09-17", request.url.queryParameter("dateFrom"))
        assertEquals("2026-10-17", request.url.queryParameter("dateTo"))
        assertEquals(OperationType.Income, page.items.single().type)
        assertEquals("salary-id", page.items.single().categoryId)
    }

    @Test
    fun `all types and all categories omit optional query filters`() = runBlocking {
        val requests = mutableListOf<Request>()
        repository(requests, """{"items":[],"nextCursor":null}""")
            .getOperations(20, null, FILTER.copy(type = null, categoryId = null))

        val request = requests.single()
        assertNull(request.url.queryParameter("type"))
        assertNull(request.url.queryParameter("categoryId"))
        assertNull(request.url.queryParameter("cursor"))
        assertEquals("2026-09-17", request.url.queryParameter("dateFrom"))
    }

    @Test
    fun `rejects unknown operation types instead of treating them as expenses`() {
        val repository = repository(mutableListOf(), response("EXPENSE_REFUND"))
        assertThrows(OperationHistoryException.InvalidResponse::class.java) {
            runBlocking { repository.getOperations(20, null, FILTER) }
        }
    }

    private fun repository(requests: MutableList<Request>, body: String): OperationHistoryRepositoryImpl {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = Retrofit.Builder()
            .baseUrl("https://synthetic.invalid/")
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OperationHistoryApi::class.java)
        return OperationHistoryRepositoryImpl(api, "synthetic-test-token")
    }
}

private val FILTER = OperationHistoryFilter.lastMonth(LocalDate.of(2026, 10, 17))
private fun response(type: String) = """
    {
      "items": [{
        "id": "entry-id",
        "type": "$type",
        "category": {"id": "salary-id", "name": "Тестовая категория"},
        "originalAmount": {"amount": "100.00", "currency": "KZT"},
        "amountKzt": {"amount": "100.00", "currency": "KZT"},
        "occurredOn": "2026-10-17",
        "status": "POSTED",
        "version": 0
      }],
      "nextCursor": null
    }
""".trimIndent()
