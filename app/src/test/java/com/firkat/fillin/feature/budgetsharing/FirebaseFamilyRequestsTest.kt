package com.firkat.fillin.feature.budgetsharing

import com.firkat.fillin.feature.auth.data.FirebaseAuthenticatedRequests
import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.budgetsharing.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class FirebaseFamilyRequestsTest {
    @Test fun `401 repeats the original join code once with the refreshed Firebase token`() = runBlocking {
        val bodies = mutableListOf<String>()
        val headers = mutableListOf<String?>()
        val refreshes = mutableListOf<Boolean>()
        val api = Retrofit.Builder().baseUrl("https://synthetic.invalid/")
            .client(
                OkHttpClient.Builder().addInterceptor { chain ->
                    val request = chain.request()
                    assertEquals("/api/v4/budgets/invitations/accept", request.url.encodedPath)
                    bodies += okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                    headers += request.header("Authorization")
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Synthetic")
                        .code(if (bodies.size == 1) 401 else 200)
                        .body(
                            (
                                if (bodies.size ==
                                    1
                                ) {
                                    "{}"
                                } else {
                                    """{"id":"budget","name":"Семейный","memberId":"member","role":"MEMBER"}"""
                                }
                                ).toResponseBody("application/json".toMediaType())
                        ).build()
                }.build()
            ).addConverterFactory(
                Json.asConverterFactory("application/json".toMediaType())
            ).build().create(FirebaseFamilyBudgetApi::class.java)
        val tokens = object : FirebaseIdTokenProvider {
            override fun isCurrentUser(
                uid: String
            ) = uid == "guest"
            override suspend fun idToken(
                uid: String,
                forceRefresh: Boolean
            ): String {
                refreshes += forceRefresh
                return if (forceRefresh) "fresh" else "cached"
            }
        }
        val result = FirebaseAuthenticatedRequests(tokens).execute("guest", api) { endpoint, token ->
            endpoint.join(token, AcceptInvitationDto("ABCDE-23456-789AB-CDEFG"))
        }
        assertTrue(result.isSuccessful)
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        assertTrue(bodies[0].contains("ABCDE-23456-789AB-CDEFG"))
        assertEquals(listOf("Bearer cached", "Bearer fresh"), headers)
        assertEquals(listOf(false, true), refreshes)
    }
}
