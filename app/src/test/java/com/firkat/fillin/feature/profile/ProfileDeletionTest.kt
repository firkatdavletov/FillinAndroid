package com.firkat.fillin.feature.profile

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.profile.data.*
import com.firkat.fillin.feature.profile.domain.*
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class ProfileDeletionTest {
    private class Store : DeletionStore {
        var operation: DeletionOperation? = null
        val blocked = mutableSetOf<String>()
        override fun operations() = listOfNotNull(operation)
        override fun blocked(
            uid: String
        ) = uid in blocked
        override fun save(
            operation: DeletionOperation
        ) {
            this.operation = operation
            blocked += operation.uid
        }
        override fun block(
            uid: String
        ) {
            blocked += uid
        }
        override fun finish(
            uid: String
        ) {
            operation = null
        }
    }
    private class Auth : AuthRepository {
        override val session =
            MutableStateFlow<AuthSession>(AuthSession.Authenticated(AuthUser("uid", null, "a@b.com")))
        var reauthenticated = false
        override suspend fun reauthenticatePassword(
            password: String
        ) {
            reauthenticated = true
        }
        override suspend fun signInWithGoogle(
            idToken: String
        ) {}
        override suspend fun signOut() {
            session.value = AuthSession.Anonymous
        }
    }
    private class Api : ProfileDeletionApi {
        var deletes = mutableListOf<String>()
        var gets = mutableListOf<String>()
        var delete: () -> Response<DeletionStatusDto> = {
            Response.success(202, DeletionStatusDto(DeletionStatus.PENDING))
        }
        var status: () -> Response<DeletionStatusDto> = { Response.success(DeletionStatusDto(DeletionStatus.PENDING)) }
        override suspend fun delete(
            authorization: String?,
            secret: String
        ): Response<DeletionStatusDto> {
            deletes +=
                secret
            return delete()
        }
        override suspend fun status(
            secret: String
        ): Response<DeletionStatusDto> {
            gets += secret
            return status()
        }
    }

    @Test fun `secret has required random URL safe format`() {
        val secrets = (1..100).map { newDeletionSecret() }
        assertTrue(secrets.all { it.matches(Regex("[A-Za-z0-9_-]{43}")) })
        assertEquals(100, secrets.toSet().size)
    }

    @Test fun `accepted deletion signs out and retains receipt until completed`() = runTest {
        val store = Store()
        val auth = Auth()
        val api = Api()
        var erased = 0
        val coordinator =
            ProfileDeletionCoordinator(store, api, auth, { "token" }, { assertTrue(store.blocked(it)) }, { erased++ })
        coordinator.begin("uid")
        assertEquals(DeletionPhase.Pending, coordinator.state.value.phase)
        assertEquals(AuthSession.Anonymous, auth.session.value)
        assertEquals(1, erased)
        val secret = store.operation!!.secret
        api.status = { Response.success(DeletionStatusDto(DeletionStatus.COMPLETED)) }
        coordinator.resume()
        assertEquals(listOf(secret), api.gets)
        assertNull(store.operation)
        assertTrue(store.blocked("uid"))
        assertEquals(DeletionPhase.Completed, coordinator.state.value.phase)
    }

    @Test fun `lost delete response is resolved through unauthenticated status`() = runTest {
        val api = Api().apply { delete = { throw IOException() } }
        val store = Store()
        val coordinator = ProfileDeletionCoordinator(store, api, Auth(), { "token" }, {}, {})
        coordinator.begin("uid")
        assertEquals(DeletionPhase.Pending, coordinator.state.value.phase)
        assertEquals(api.deletes, api.gets)
        assertTrue(store.operation!!.accepted)
    }

    @Test fun `restart checks status before repeating delete and reuses secret`() = runTest {
        val api = Api().apply { status = { Response.error(404, "{\"code\":\"deletion_not_found\"}".toResponseBody()) } }
        val store = Store().apply { save(DeletionOperation("uid", newDeletionSecret())) }
        val secret = store.operation!!.secret
        ProfileDeletionCoordinator(store, api, Auth(), { "token" }, {}, {}).resume()
        assertEquals(listOf(secret), api.gets)
        assertEquals(listOf(secret), api.deletes)
    }

    @Test fun `recent login requires reauthentication and preserves secret`() = runTest {
        val api = Api().apply {
            delete = { Response.error(403, "{\"code\":\"recent_login_required\"}".toResponseBody()) }
            status = { Response.error(404, "{\"code\":\"deletion_not_found\"}".toResponseBody()) }
        }
        val store = Store()
        val auth = Auth()
        val coordinator = ProfileDeletionCoordinator(store, api, auth, { "token" }, {}, {})
        coordinator.begin("uid")
        assertEquals(DeletionPhase.Reauthenticate, coordinator.state.value.phase)
        api.delete = { Response.success(202, DeletionStatusDto(DeletionStatus.PENDING)) }
        coordinator.reauthenticate(password = "password")
        assertTrue(auth.reauthenticated)
        assertEquals(1, api.deletes.toSet().size)
        assertEquals(DeletionPhase.Pending, coordinator.state.value.phase)
    }

    @Test fun `cleanup failure is durable and retried after restart`() = runTest {
        val store = Store()
        val api = Api()
        val auth = Auth()
        ProfileDeletionCoordinator(store, api, auth, { "token" }, {}, { throw IOException() }).begin("uid")
        assertTrue(store.operation!!.accepted)
        assertFalse(store.operation!!.cleaned)
        var cleaned = false
        ProfileDeletionCoordinator(store, api, auth, { "token" }, {}, { cleaned = true }).resume()
        assertTrue(cleaned)
        assertTrue(store.operation!!.cleaned)
        assertEquals(1, api.deletes.size)
    }

    @Test fun `unauthorized and conflict never mean success or erase local data`() = runTest {
        for (code in listOf(
            "unauthorized",
            "account_deletion_in_progress",
            "invalid_deletion_token",
            "deletion_unavailable",
            "auth_unavailable"
        )) {
            val api = Api().apply {
                delete = { Response.error(401, "{\"code\":\"$code\"}".toResponseBody()) }
                status = { Response.error(404, "{\"code\":\"deletion_not_found\"}".toResponseBody()) }
            }
            val store = Store()
            var erased = false
            val coordinator = ProfileDeletionCoordinator(store, api, Auth(), { "token" }, {}, { erased = true })
            coordinator.begin("uid")
            assertFalse(erased)
            assertFalse(store.operation!!.accepted)
            assertEquals(DeletionPhase.Failed, coordinator.state.value.phase)
        }
    }

    @Test fun `account switch during token acquisition prevents delete`() = runTest {
        val auth = Auth()
        val api = Api()
        val store = Store()
        val coordinator =
            ProfileDeletionCoordinator(store, api, auth, {
                auth.session.value = AuthSession.Anonymous
                "token"
            }, {}, {})
        coordinator.begin("uid")
        assertTrue(api.deletes.isEmpty())
        assertTrue(store.blocked("uid"))
    }
}
