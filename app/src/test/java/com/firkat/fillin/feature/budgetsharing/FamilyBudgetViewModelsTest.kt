package com.firkat.fillin.feature.budgetsharing

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.AvailableBudget
import com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
import com.firkat.fillin.feature.budgetsharing.domain.*
import com.firkat.fillin.feature.budgetsharing.ui.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyBudgetViewModelsTest {
    private val models = ViewModelStore()
    private val code = "ABCDE-23456-789AB-CDEFG"
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `normalization accepts pasted lowercase groups and rejects malformed or oversized codes`() {
        assertEquals(code, FamilyInvitationCode.normalize(" abcde 23456 789ab cdefg "))
        for (invalid in listOf("ABC", "I".repeat(20), "O".repeat(20), " ".repeat(81) + code)) {
            assertTrue(runCatching { FamilyInvitationCode.normalize(invalid) }.isFailure)
        }
    }
    @Test fun `join validates before HTTP blocks duplicate taps and keeps a lost response retry`() = runTest {
        val auth = Auth(); val repository = Repository()
        val model = FamilyBudgetJoinViewModel(auth, repository).also { models.put("join", it) }
        runCurrent()
        model.editCode("bad"); model.join(); runCurrent()
        assertEquals(0, repository.joins)
        model.editCode(code.lowercase()); repository.gate = CompletableDeferred()
        model.join(); model.join(); runCurrent()
        assertEquals(1, repository.joins)
        assertTrue(model.state.value.busy)
        repository.failJoin = true; repository.gate!!.complete(Unit); runCurrent()
        assertEquals(code.lowercase(), model.state.value.code)
        assertNotNull(model.state.value.error)
        repository.failJoin = false; repository.gate = null
        model.join(); runCurrent()
        assertEquals("Семейный", model.state.value.joinedName)
        assertEquals("", model.state.value.code)
        assertEquals(2, repository.joins)
        assertEquals(code, repository.lastCode)
    }
    @Test fun `account change clears the code and discards a late join response`() = runTest {
        val auth = Auth(); val repository = Repository().apply { gate = CompletableDeferred() }
        val model = FamilyBudgetJoinViewModel(auth, repository).also { models.put("join", it) }
        runCurrent(); model.editCode(code); model.join(); runCurrent()
        auth.session.value = AuthSession.Authenticated(AuthUser("other", null, null)); runCurrent()
        repository.gate!!.complete(Unit); runCurrent()
        assertEquals("", model.state.value.code)
        assertNull(model.state.value.joinedName)
        assertFalse(model.state.value.busy)
    }
    @Test fun `OWNER can revoke MEMBER once while MEMBER cannot revoke anyone`() = runTest {
        val repository = Repository()
        val model = FamilyMembersViewModel(repository).also { models.put("members", it) }
        runCurrent()
        model.revoke(repository.owner); runCurrent()
        assertEquals(0, repository.revokes)
        repository.gate = CompletableDeferred()
        model.revoke(repository.guest); model.revoke(repository.guest); runCurrent()
        assertEquals(1, repository.revokes)
        repository.gate!!.complete(Unit); runCurrent()
        assertEquals(listOf(repository.owner), model.state.value.members)
        repository.rows = listOf(repository.owner, repository.guest)
        repository.target.value = repository.target.value!!.copy(role = "MEMBER"); runCurrent()
        model.revoke(repository.guest); runCurrent()
        assertEquals(1, repository.revokes)
    }
    @Test fun `members from an old budget are hidden before an uncancellable response arrives`() = runTest {
        val repository = Repository()
        val model = FamilyMembersViewModel(repository).also { models.put("members", it) }
        runCurrent()
        val gate = CompletableDeferred<Unit>(); repository.readGate = gate
        model.refresh(); runCurrent()
        repository.readGate = null
        repository.target.value = repository.target.value!!.copy(budgetId = "other-budget", profileId = "other-profile")
        repository.rows = emptyList(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertTrue(model.state.value.members.isEmpty())
        assertEquals("other-budget", model.state.value.target!!.budgetId)
        repository.target.value = null; runCurrent()
        assertNull(model.state.value.target)
    }
    private class Auth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Authenticated(AuthUser("owner", null, null)))
        override suspend fun signInWithGoogle(idToken: String) = Unit
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
    }
    private class Repository : FamilyBudgetRepository {
        override val target = MutableStateFlow<InvitationTarget?>(InvitationTarget("profile", "owner", "budget", "OWNER"))
        override fun isCurrent(target: InvitationTarget) = this.target.value == target
        val owner = FamilyBudgetMember("owner-member", "Owner", "OWNER", "2026-10-23T00:00:00Z", true)
        val guest = FamilyBudgetMember("guest-member", "Guest", "MEMBER", "2026-10-23T00:00:00Z", false)
        var rows = listOf(owner, guest)
        var joins = 0; var revokes = 0; var lastCode: String? = null
        var gate: CompletableDeferred<Unit>? = null
        var readGate: CompletableDeferred<Unit>? = null
        var failJoin = false
        override suspend fun join(uid: String, code: String): AvailableBudget {
            joins++; lastCode = code
            withContext(NonCancellable) { gate?.await() }
            if (failJoin) throw BudgetConnectionFailure("network")
            return AvailableBudget("family", "Семейный", "guest-member", "MEMBER")
        }
        override suspend fun members(target: InvitationTarget): List<FamilyBudgetMember> {
            val result = rows
            withContext(NonCancellable) { readGate?.await() }
            return result
        }
        override suspend fun revoke(target: InvitationTarget, member: FamilyBudgetMember) {
            revokes++; withContext(NonCancellable) { gate?.await() }; rows = rows.filterNot { it.id == member.id }
        }
    }
}
