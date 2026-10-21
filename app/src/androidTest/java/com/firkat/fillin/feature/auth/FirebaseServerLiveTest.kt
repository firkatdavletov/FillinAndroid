package com.firkat.fillin.feature.auth

import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.auth.data.FirebaseAuthApi
import com.firkat.fillin.feature.auth.data.FirebaseServerAuthRepository
import com.firkat.fillin.feature.auth.data.ServerUserDto
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.ServerAuthRepository
import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import retrofit2.Retrofit

/** Opt-in only: requires a real Google session and a separately configured test server. */
class FirebaseServerLiveTest {
    @Test fun restoredGoogleSessionReturnsTheSameServerUserOnRepeatedRequests() = runBlocking {
        val koin = GlobalContext.get()
        val uid = authenticatedUid()
        val server = koin.get<ServerAuthRepository>()
        val first = server.authenticate(uid)
        val repeated = server.authenticate(uid)
        // Boolean assertions keep real identifiers and account details out of test output.
        assertTrue("Server must return the verified current account", first.firebaseUid == uid)
        assertTrue("Server user ID must be present", first.id.isNotBlank())
        assertTrue("Repeated authentication must keep server identity", repeated.id == first.id)
        assertTrue("Repeated authentication must keep the current account", repeated.firebaseUid == uid)
    }

    @Test fun controlled401RefreshesTheRealFirebaseTokenAndAuthenticatesOnTheTestServer() = runBlocking {
        val uid = authenticatedUid()
        val koin = GlobalContext.get()
        val realApi = koin.get<Retrofit>().newBuilder().baseUrl(BuildConfig.FIREBASE_API_BASE_URL)
            .client(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build())
            .build().create(FirebaseAuthApi::class.java)
        var calls = 0
        val controlledApi = object : FirebaseAuthApi {
            override suspend fun authenticate(authorization: String): Response<ServerUserDto> =
                if (++calls == 1) Response.error(401, "{}".toResponseBody()) else realApi.authenticate(authorization)
        }
        val actualTokens = koin.get<FirebaseIdTokenProvider>()
        val refreshes = mutableListOf<Boolean>()
        val tokens = object : FirebaseIdTokenProvider {
            override fun isCurrentUser(uid: String) = actualTokens.isCurrentUser(uid)
            override suspend fun idToken(uid: String, forceRefresh: Boolean): String {
                refreshes += forceRefresh
                return actualTokens.idToken(uid, forceRefresh)
            }
        }
        val profile = FirebaseServerAuthRepository(controlledApi, tokens).authenticate(uid)
        assertTrue("Retry must authenticate the restored real account", profile.firebaseUid == uid)
        assertTrue("One controlled 401 must cause exactly one retry", calls == 2)
        assertTrue("Retry must force a real SDK token refresh", refreshes == listOf(false, true))
    }

    private suspend fun authenticatedUid(): String {
        assumeTrue(
            "Run explicitly with -e liveFirebaseAuth true after Google sign-in",
            InstrumentationRegistry.getArguments().getString("liveFirebaseAuth") == "true",
        )
        return withTimeout(10_000) {
            GlobalContext.get().get<AuthRepository>().session
                .filterIsInstance<AuthSession.Authenticated>().first().user.uid
        }
    }
}
