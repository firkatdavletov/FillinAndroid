package com.firkat.fillin.feature.operationhistory.data.sync

import com.firkat.fillin.feature.operationhistory.data.remote.CreateOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OperationSyncHttpTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `creation explicitly transmits required currency even with default JSON configuration`() =
        runBlocking {
            val requests = mutableListOf<Request>()
            val api =
                api(
                    requests,
                    """{
            "requestId":"request-uuid","operation":{
                "id":"server-uuid","categoryId":"category-uuid","type":"EXPENSE",
                "originalAmount":"10.01","originalCurrency":"KZT","amountKzt":"10.01",
                "occurredOn":"2026-10-18","createdAt":"2026-10-18T05:00:00Z",
                "status":"POSTED","version":0,"sequence":1,"requestId":"request-uuid"
            }
        }""",
                )
            val request =
                CreateOperationDto(
                    "request-uuid",
                    "household-uuid",
                    "member-uuid",
                    "EXPENSE",
                    "category-uuid",
                    "10.01",
                    "2026-10-18",
                )
            repeat(2) {
                assertEquals("request-uuid", api.create("Bearer synthetic-test-token", request).body()!!.requestId)
            }
            val bodies =
                requests.map {
                    assertEquals("/api/v1/operations", it.url.encodedPath)
                    assertEquals("POST", it.method)
                    assertEquals("Bearer synthetic-test-token", it.header("Authorization"))
                    val buffer = Buffer()
                    it.body!!.writeTo(buffer)
                    buffer.readUtf8()
                }
            assertEquals(bodies[0], bodies[1])
            val body = json.parseToJsonElement(bodies[0]).jsonObject
            assertEquals("KZT", body["currency"]!!.jsonPrimitive.content)
            assertEquals("household-uuid", body["householdId"]!!.jsonPrimitive.content)
            assertEquals("member-uuid", body["memberId"]!!.jsonPrimitive.content)
            assertEquals("10.01", body["amount"]!!.jsonPrimitive.content)
            assertTrue(body["amount"]!!.jsonPrimitive.isString)
        }

    @Test
    fun `sync sends opaque cursor unchanged and decodes server scope and terminal page`() =
        runBlocking {
            val requests = mutableListOf<Request>()
            val api =
                api(
                    requests,
                    """{
            "scope":{"householdId":"household-uuid","memberId":"member-uuid","role":"OWNER",
              "accountingStartedAt":"2026-09-01T19:00:00Z","timeZone":"Asia/Almaty","currency":"KZT"},
            "changes":[],"nextCursor":"next-opaque-cursor","hasMore":false
        }""",
                )
            api.changes("Bearer synthetic-test-token", null)
            val page = api.changes("Bearer synthetic-test-token", "opaque:+/%=")
            assertNull(requests.first().url.queryParameter("cursor"))
            assertEquals("opaque:+/%=", requests.last().url.queryParameter("cursor"))
            assertEquals("/api/v3/sync/changes", requests.last().url.encodedPath)
            assertEquals("household-uuid:member-uuid", page.scope.key)
            assertEquals("next-opaque-cursor", page.nextCursor)
            assertFalse(page.hasMore)
        }

    private fun api(
        requests: MutableList<Request>,
        body: String,
    ): OperationSyncApi {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor {
                    requests += it.request()
                    Response
                        .Builder()
                        .request(it.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return Retrofit
            .Builder()
            .baseUrl("https://synthetic.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OperationSyncApi::class.java)
    }
}
