package com.firkat.fillin.feature.budgetconnection.data

import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class FirebaseBudgetCreationRepository(
    private val requests: FirebaseBudgetRequests,
    private val store: BudgetCreationStore,
    private val connection: BudgetConnectionRepository,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : BudgetCreationRepository {
    private val mutex = Mutex()
    suspend fun awaitIdle() = mutex.withLock { }
    override suspend fun pending(uid: String): PendingBudgetCreation? {
        requests.ensureCurrent(uid)
        return store.pending(uid).also { requests.ensureCurrent(uid) }
    }
    override suspend fun create(uid: String, draft: BudgetCreationDraft) = mutex.withLock {
        requests.ensureCurrent(uid)
        val valid = draft.validated(LocalDate.now(clock))
        val saved = store.save(uid, PendingBudgetCreation(UUID.randomUUID().toString(), valid))
        if (saved.draft != valid) throw BudgetConnectionFailure("creation_pending")
        send(uid, saved)
    }
    override suspend fun resume(uid: String) = mutex.withLock {
        val saved = pending(uid) ?: throw BudgetConnectionFailure("creation_not_found")
        send(uid, saved)
    }
    private suspend fun send(uid: String, saved: PendingBudgetCreation) {
        try {
            val draft = saved.draft
            val result = requests.create(uid, CreateFirebaseBudgetDto(saved.requestId, draft.name, draft.openingBalance, draft.accountingStartedOn)).budgetBody()
            try {
                UUID.fromString(result.id); UUID.fromString(result.memberId)
                require(result.name == draft.name && result.role == "OWNER")
            } catch (_: IllegalArgumentException) { throw BudgetConnectionFailure("invalid_response") }
            connection.connect(uid, AvailableBudget(result.id, result.name, result.memberId, result.role))
            // Selection replaces the budget ViewModelStore and can cancel the caller at this point.
            withContext(NonCancellable) { store.complete(uid, saved.requestId) }
        } catch (error: HttpException) {
            error.response()?.errorBody()?.close()
            if (error.code() == 400) withContext(NonCancellable) { store.complete(uid, saved.requestId) }
            throw BudgetConnectionFailure(when (error.code()) {
                400 -> "invalid_creation"; 401 -> "unauthorized"; 403 -> "access_denied"
                409 -> "creation_conflict"; else -> "server_unavailable"
            })
        }
    }
}
