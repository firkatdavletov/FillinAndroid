package com.firkat.fillin.feature.budgetsetup

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
import com.firkat.fillin.feature.budgetsharing.domain.*
import com.firkat.fillin.feature.profile.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.time.LocalDate

internal class SetupTestEnvironment {
    val session = MutableStateFlow<AuthSession>(AuthSession.Anonymous)
    val auth = object : AuthRepository {
        override val session = this@SetupTestEnvironment.session
        override suspend fun signInWithGoogle(idToken: String) { session.value = AuthSession.Authenticated(AuthUser(idToken, null, null)) }
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
    }
    var available = emptyList<AvailableBudget>()
    var saved: PendingBudgetCreation? = null
    var creates = 0
    var resumes = 0
    var localCreates = 0
    var submitted: BudgetCreationDraft? = null
    var gate: CompletableDeferred<Unit>? = null
    var fail = false
    val connection = object : BudgetConnectionRepository {
        override suspend fun budgets(uid: String) = available
        override suspend fun connect(uid: String, budget: AvailableBudget) {}
    }
    val creation = object : BudgetCreationRepository {
        override suspend fun pending(uid: String) = saved
        override suspend fun create(uid: String, draft: BudgetCreationDraft) {
            creates++; submitted = draft
            gate?.await()
            if (fail) { saved = PendingBudgetCreation("same-request", draft); error("offline") }
        }
        override suspend fun resume(uid: String) { resumes++; saved = null }
    }
    val local = com.firkat.fillin.feature.budgetsetup.domain.LocalBudgetCreator { name, balance, startedOn ->
        localCreates++; submitted = BudgetCreationDraft(requireNotNull(name), balance.toPlainString(), startedOn.toString()); gate?.await()
    }

    val family = object : FamilyBudgetRepository {
        override val target = flowOf<InvitationTarget?>(null)
        override fun isCurrent(target: InvitationTarget) = false
        override suspend fun join(uid: String, code: String) = AvailableBudget("joined", "Семья", "member", "MEMBER")
        override suspend fun members(target: InvitationTarget) = emptyList<FamilyBudgetMember>()
        override suspend fun revoke(target: InvitationTarget, member: FamilyBudgetMember) {}
    }
    fun login(uid: String = "owner") { session.value = AuthSession.Authenticated(AuthUser(uid, null, null)) }
}
