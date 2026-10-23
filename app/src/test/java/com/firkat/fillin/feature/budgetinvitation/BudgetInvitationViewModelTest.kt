package com.firkat.fillin.feature.budgetinvitation

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.budgetinvitation.domain.*
import com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationViewModel
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetInvitationViewModelTest {
    private val models = ViewModelStore()
    private val now = Instant.parse("2026-10-23T00:00:00Z")
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `owner creates copies replaces and revokes with duplicate taps blocked`() = runTest {
        val repository = Repository()
        val model = model(repository)
        runCurrent()
        repository.finish = CompletableDeferred()
        model.create(); model.create(); runCurrent()
        assertEquals(1, repository.creates)
        assertTrue(model.state.value.busy)
        repository.finish!!.complete(Unit); runCurrent()
        val first = model.state.value.current!!
        var copied: String? = null
        model.copyCode { copied = it }
        assertEquals("00001-00001-00001-00001", copied)
        assertTrue(model.state.value.copied)
        repository.finish = null
        model.create(); runCurrent()
        assertNotEquals(first.id, model.state.value.current!!.id)
        assertFalse(model.state.value.copied)
        model.revoke(); runCurrent()
        assertNull(model.state.value.current)
        assertNull(model.state.value.code)
        assertEquals(1, repository.revokes)
    }

    @Test fun `budget or account change hides code and discards an uncancellable old response`() = runTest {
        val repository = Repository()
        val model = model(repository)
        runCurrent()
        repository.finish = CompletableDeferred()
        model.create(); runCurrent()
        val old = repository.target.value!!
        repository.target.value = old.copy(profileId = "second", budgetId = "second-budget")
        runCurrent()
        repository.finish!!.complete(Unit); runCurrent()
        assertEquals("second-budget", model.state.value.target!!.budgetId)
        assertNull(model.state.value.code)
        assertNull(model.state.value.current)
        repository.target.value = null; runCurrent()
        assertNull(model.state.value.target)
        model.copyCode { fail("Old code copied after logout") }
    }

    @Test fun `MEMBER cannot read or manage invites and reopening never restores the plaintext code`() = runTest {
        val repository = Repository().apply { target.value = target.value!!.copy(role = "MEMBER") }
        val model = model(repository)
        runCurrent()
        model.create(); model.refresh(); model.revoke(); runCurrent()
        assertEquals(0, repository.reads)
        assertEquals(0, repository.creates)
        repository.target.value = repository.target.value!!.copy(role = "OWNER"); runCurrent()
        model.create(); runCurrent()
        val invitation = model.state.value.current
        models.clear()
        val reopened = model(repository); runCurrent()
        assertEquals(invitation, reopened.state.value.current)
        assertNull(reopened.state.value.code)
        repository.target.value = repository.target.value!!.copy(uid = "another")
        reopened.copyCode { fail("Code copied during an account transition") }
    }

    @Test fun `expiration removes code and a lost creation response never leaves the previous code copyable`() = runTest {
        val repository = Repository()
        val model = model(repository)
        runCurrent()
        model.create(); runCurrent()
        advanceTimeBy(24 * 60 * 60 * 1000L); runCurrent()
        assertTrue(model.state.value.expired)
        assertNull(model.state.value.code)
        model.copyCode { fail("Expired code copied") }
        model.create(); runCurrent()
        repository.loseResponse = true
        model.create(); runCurrent()
        assertNull(model.state.value.code)
        assertNotNull(model.state.value.error)
        model.refresh(); runCurrent()
        assertEquals(repository.saved, model.state.value.current)
        assertNull(model.state.value.code)
    }

    private fun model(repository: Repository) = BudgetInvitationViewModel(repository, Clock.fixed(now, ZoneOffset.UTC))
        .also { models.put("invitation", it) }
    private inner class Repository : BudgetInvitationRepository {
        override val target = MutableStateFlow<InvitationTarget?>(InvitationTarget("first", "owner", "budget", "OWNER"))
        override fun isCurrent(target: InvitationTarget) = this.target.value == target
        var creates = 0
        var reads = 0
        var revokes = 0
        var saved: BudgetInvitation? = null
        var finish: CompletableDeferred<Unit>? = null
        var loseResponse = false
        override suspend fun current(target: InvitationTarget): BudgetInvitation? { reads++; return saved?.takeIf { it.budgetId == target.budgetId } }
        override suspend fun create(target: InvitationTarget): CreatedInvitation {
            creates++
            val number = creates.toString().padStart(5, '0')
            val created = CreatedInvitation(BudgetInvitation("invite-$creates", target.budgetId, now.plusSeconds(86400).toString()), List(4) { number }.joinToString("-"))
            withContext(NonCancellable) { finish?.await() }
            saved = created.invitation
            if (loseResponse) { loseResponse = false; throw BudgetConnectionFailure("network") }
            return created
        }
        override suspend fun revoke(target: InvitationTarget, invitation: BudgetInvitation) { revokes++; saved = null }
    }
}
