package com.firkat.fillin.feature.operationhistory.data.sync

import com.firkat.fillin.feature.budgetconnection.data.FirebaseBudgetApi
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ConfirmedPlanLinkHttpTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun `budget capabilities and suggestion travel through Firebase scoped endpoints`() = runBlocking {
        MockWebServer().use { server ->
            val api = api(server)
            server.enqueue(MockResponse().setBody("""{"scope":{"householdId":"h","memberId":"m","role":"OWNER","accountingStartedAt":"2026-10-01T00:00:00Z","timeZone":"Asia/Almaty","currency":"KZT"},"capabilities":["confirmedPlanLinkingV1"]}"""))
            assertTrue("confirmedPlanLinkingV1" in api.budget("Bearer synthetic", "h").body()!!.capabilities)
            val budgetRequest = server.takeRequest()
            assertEquals("/api/v4/budgets/h", budgetRequest.path)
            assertEquals("confirmedPlanLinkingV1,dailyLimitPlanningV1", budgetRequest.getHeader("X-Fillin-Capabilities"))
            server.enqueue(MockResponse().setBody("""{"requestId":"create","revision":42,"entityToken":1,"suggestedPlan":{"target":{"type":"INCOME","planId":"plan","dueOn":"2026-10-30"},"categoryId":"category","categoryName":"Зарплата","plannedAmount":"100.00","remainingAmount":"80.00","version":2}}"""))
            val command = BudgetSyncCommand("create", "h", "m", "CREATE_OPERATION", "op", "2026-10-30", categoryId = "category", kind = "INCOME", amount = "30.00", occurredOn = "2026-10-30", planMatchingMode = "CONFIRM_REQUIRED")
            val response = api.command("Bearer synthetic", "h", command).body()!!
            assertEquals("plan", response.suggestedPlan!!.target.planId)
            assertEquals("80.00", response.suggestedPlan.remainingAmount)
            val request = server.takeRequest()
            assertEquals("/api/v4/budgets/h/sync/commands", request.path)
            assertEquals("Bearer synthetic", request.getHeader("Authorization"))
            assertEquals("confirmedPlanLinkingV1,dailyLimitPlanningV1", request.getHeader("X-Fillin-Capabilities"))
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("CONFIRM_REQUIRED", body.getValue("planMatchingMode").jsonPrimitive.content)
            assertEquals("30.00", body.getValue("amount").jsonPrimitive.content)
            assertTrue(body.getValue("amount").jsonPrimitive.isString)
        }
    }

    @Test fun `retry preserves request identity and occurrence target rather than projected UUID`() = runBlocking {
        MockWebServer().use { server ->
            val api = api(server)
            val command = BudgetSyncCommand("link", "h", "m", "SET_OPERATION_PLAN_LINK", "op", "2026-10-30", expectedToken = 1,
                planTarget = PlanLinkTarget("EXPENSE", ruleId = "rule", dueOn = "2026-10-30"), expectedPlanVersion = 0)
            repeat(2) { server.enqueue(MockResponse().setBody("""{"requestId":"link","revision":43,"entityToken":2}""")) }
            repeat(2) { assertEquals(2L, api.command("Bearer synthetic", "h", command).body()!!.entityToken) }
            val first = server.takeRequest().body.readUtf8()
            assertEquals(first, server.takeRequest().body.readUtf8())
            val target = json.parseToJsonElement(first).jsonObject.getValue("planTarget").jsonObject
            assertFalse(target.containsKey("planId"))
            assertEquals("rule", target.getValue("ruleId").jsonPrimitive.content)
            assertEquals("2026-10-30", target.getValue("dueOn").jsonPrimitive.content)
        }
    }

    private fun api(server: MockWebServer): FirebaseBudgetApi = Retrofit.Builder().baseUrl(server.url("/"))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(FirebaseBudgetApi::class.java)
}
