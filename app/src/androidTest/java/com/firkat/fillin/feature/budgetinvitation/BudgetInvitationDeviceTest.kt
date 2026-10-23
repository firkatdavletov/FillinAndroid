package com.firkat.fillin.feature.budgetinvitation

import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetconnection.MultipleBudgetsDeviceTest
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetinvitation.data.*
import com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class BudgetInvitationDeviceTest {
    @Test fun invitationsFollowSelectedCloudBudgetAndNeverPublishResponsesAcrossBudgetOrAccountChanges() = runBlocking {
        val budgets = MultipleBudgetsDeviceTest.MultiServer()
        BudgetConnectionDeviceTest.Environment(apiOverride = budgets).use { env ->
            env.auth.login("owner"); env.manager.restore()
            env.creation.create("owner", draft("Первый"))
            val first = env.manager.active.value!!.profile
            env.creation.create("owner", draft("Второй"))
            val second = env.manager.active.value!!.profile
            val api = Api()
            val repository = repository(env, api)
            val secondTarget = withTimeout(5000) { repository.target.first { it?.budgetId == second.budgetId } }!!
            val secondInvite = repository.create(secondTarget)
            env.connection.connect("owner", env.connection.cachedBudgets("owner").single { it.id == first.budgetId })
            val firstTarget = withTimeout(5000) { repository.target.first { it?.budgetId == first.budgetId } }!!
            val firstInvite = repository.create(firstTarget)
            assertNotEquals(firstInvite.invitation.id, secondInvite.invitation.id)
            val calls = api.calls
            assertEquals("account_changed", (runCatching { repository.revoke(secondTarget, secondInvite.invitation) }.exceptionOrNull() as BudgetConnectionFailure).code)
            assertEquals(calls, api.calls)
            val entered = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Unit>()
            api.beforeReturn = { entered.complete(Unit); response.await() }
            val oldRequest = async(Dispatchers.IO) { runCatching { repository.create(firstTarget) }.exceptionOrNull() }
            withTimeout(5000) { entered.await() }
            env.connection.connect("owner", env.connection.cachedBudgets("owner").single { it.id == second.budgetId })
            response.complete(Unit)
            assertEquals("account_changed", (withTimeout(5000) { oldRequest.await() } as BudgetConnectionFailure).code)
            assertEquals(secondInvite.invitation.id, api.saved.getValue(second.budgetId!!).id)
            api.beforeReturn = null
            env.auth.logout()
            assertFalse(repository.isCurrent(secondTarget))
            assertEquals("account_changed", (runCatching { repository.current(secondTarget) }.exceptionOrNull() as BudgetConnectionFailure).code)
            assertNull(withTimeout(5000) { repository.target.first() })
        }
    }

    @Test fun foreignBudgetResponseIsRejectedAndMEMBERCannotStartAnInvitationRequest() = runBlocking {
        val server = BudgetConnectionDeviceTest.Server()
        BudgetConnectionDeviceTest.Environment(server).use { env ->
            env.auth.login("owner"); env.manager.restore()
            env.connection.connect("owner", env.connection.budgets("owner").single())
            val api = Api().apply { wrongBudget = true }
            val repository = repository(env, api)
            val target = withTimeout(5000) { repository.target.first { it != null } }!!
            assertEquals("invalid_response", (runCatching { repository.create(target) }.exceptionOrNull() as BudgetConnectionFailure).code)
            val profile = env.manager.active.value!!.profile
            env.database(profile) { db -> val state = db.operations().state()!!; db.operations().putState(state.copy(role = "MEMBER")) }
            val member = withTimeout(5000) { repository.target.first { it?.role == "MEMBER" } }!!
            val calls = api.calls
            assertEquals("access_denied", (runCatching { repository.create(member) }.exceptionOrNull() as BudgetConnectionFailure).code)
            assertEquals(calls, api.calls)
        }
    }

    internal class Api : FirebaseBudgetInvitationApi {
        val saved = linkedMapOf<String, InvitationDto>()
        var calls = 0
        var creations = 0
        var wrongBudget = false
        var beforeReturn: (suspend () -> Unit)? = null
        override suspend fun current(authorization: String, budgetId: String): Response<InvitationStatusDto> {
            check(authorization == "Bearer synthetic-owner"); calls++
            return Response.success(InvitationStatusDto(budgetId, saved[budgetId]?.copy(code = null)))
        }
        override suspend fun create(authorization: String, budgetId: String): Response<InvitationDto> {
            check(authorization == "Bearer synthetic-owner"); calls++; creations++
            val code = UUID.randomUUID().toString().replace("-", "").take(20).uppercase().chunked(5).joinToString("-")
            val dto = InvitationDto(UUID.randomUUID().toString(), if (wrongBudget) UUID.randomUUID().toString() else budgetId,
                Instant.now().plusSeconds(86400).toString(), code)
            saved[budgetId] = dto
            beforeReturn?.invoke()
            return Response.success(dto)
        }
        override suspend fun revoke(authorization: String, budgetId: String, invitationId: String): Response<Unit> {
            check(authorization == "Bearer synthetic-owner"); calls++
            if (saved[budgetId]?.id == invitationId) saved.remove(budgetId)
            return Response.success<Unit>(204, null)
        }
    }
    companion object {
        internal fun repository(env: BudgetConnectionDeviceTest.Environment, api: FirebaseBudgetInvitationApi) = FirebaseBudgetInvitationRepository(api,
            object : FirebaseIdTokenProvider {
                override fun isCurrentUser(uid: String) = (env.auth.session.value as? AuthSession.Authenticated)?.user?.uid == uid
                override suspend fun idToken(uid: String, forceRefresh: Boolean) = "synthetic-$uid"
            }, env.auth, env.manager)
        internal fun draft(name: String) = BudgetCreationDraft(name, "100.00", LocalDate.now().minusDays(1).toString())
    }
}
