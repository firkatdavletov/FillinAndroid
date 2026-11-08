package com.firkat.fillin.feature.profile.data

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.profile.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable stages precede destructive work. No UI coroutine owns the deletion operation. */
class ProfileDeletionCoordinator(
    private val store: DeletionStore,
    private val api: ProfileDeletionApi?,
    private val auth: AuthRepository,
    private val token: suspend (String) -> String,
    private val pause: suspend (String) -> Unit,
    private val erase: suspend (String) -> Unit,
) : ProfileDeletion {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ProfileDeletionState())
    override val state = mutableState.asStateFlow()
    private var polling: Job? = null
    fun start() { scope.launch { resume() } }
    fun setVisible(visible: Boolean) {
        polling?.cancel()
        if (visible) polling = scope.launch {
            var interval = 10_000L
            while (isActive) {
                resume()
                interval = if (state.value.phase == DeletionPhase.Failed) (interval * 2).coerceAtMost(60_000L) else 10_000L
                delay(interval)
            }
        }
    }
    fun submit(uid: String) { scope.launch { begin(uid) } }
    fun retry() { scope.launch { resume() } }
    fun confirmPassword(password: String) { scope.launch { reauthenticate(password = password) } }
    fun authenticationFailed() { mutableState.value = ProfileDeletionState(DeletionPhase.Reauthenticate, "reauthentication_failed") }
    fun confirmGoogle(token: String) { scope.launch { reauthenticate(googleToken = token) } }
    override suspend fun begin(uid: String) = mutex.withLock {
        if (auth.session.value.userOrNull?.uid != uid) return@withLock
        val existing = try { store.operations().firstOrNull() }
        catch (_: Exception) { mutableState.value = ProfileDeletionState(DeletionPhase.Failed, "storage_unavailable"); return@withLock }
        if (existing != null && existing.uid != uid) return@withLock
        val operation = try { existing ?: DeletionOperation(uid, newDeletionSecret()).also(store::save) }
        catch (_: Exception) { mutableState.value = ProfileDeletionState(DeletionPhase.Failed, "storage_unavailable"); return@withLock }
        attempt(operation, checkStatus = existing != null)
    }
    override suspend fun resume() = mutex.withLock {
        try { store.operations().firstOrNull()?.let { attempt(it, checkStatus = true) } }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; mutableState.value = ProfileDeletionState(DeletionPhase.Failed, "storage_unavailable") }
        Unit
    }
    override suspend fun reauthenticate(password: String?, googleToken: String?) = mutex.withLock {
        val operation = store.operations().firstOrNull() ?: return@withLock
        try {
            check(auth.session.value.userOrNull?.uid == operation.uid)
            if (googleToken != null) auth.reauthenticateGoogle(googleToken) else auth.reauthenticatePassword(requireNotNull(password))
            check(auth.session.value.userOrNull?.uid == operation.uid)
            attempt(operation, checkStatus = true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.value = ProfileDeletionState(DeletionPhase.Reauthenticate, "reauthentication_failed") }
    }
    private suspend fun attempt(original: DeletionOperation, checkStatus: Boolean) {
        var operation = original
        mutableState.value = ProfileDeletionState(DeletionPhase.Working)
        try {
            pause(operation.uid)
            if (operation.accepted) {
                operation = clean(operation)
            }
            val result = if (operation.completed) DeletionStatus.COMPLETED else if (checkStatus) {
                try { endpoint().status(operation.secret).result() }
                catch (failure: DeletionFailure) {
                    if (failure.code != "deletion_not_found" || operation.accepted) throw failure
                    send(operation)
                }
            } else send(operation)
            operation = operation.copy(accepted = true, completed = result == DeletionStatus.COMPLETED)
            store.save(operation)
            operation = clean(operation)
            if (operation.completed && operation.cleaned) {
                store.finish(operation.uid)
                mutableState.value = ProfileDeletionState(DeletionPhase.Completed)
            } else mutableState.value = ProfileDeletionState(DeletionPhase.Pending)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            val code = (failure as? DeletionFailure)?.code ?: "network_or_cleanup"
            mutableState.value = ProfileDeletionState(if (code == "recent_login_required") DeletionPhase.Reauthenticate else DeletionPhase.Failed, code)
        }
    }
    private fun endpoint() = api ?: throw DeletionFailure("server_not_configured")
    private suspend fun send(operation: DeletionOperation): DeletionStatus {
        if (auth.session.value.userOrNull?.uid != operation.uid) throw DeletionFailure("sign_in_required")
        val authorization = token(operation.uid)
        if (auth.session.value.userOrNull?.uid != operation.uid) throw DeletionFailure("account_changed")
        return try { endpoint().delete("Bearer $authorization", operation.secret).result() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (failure is DeletionFailure && failure.code !in setOf("unauthorized", "auth_unavailable", "deletion_unavailable")) throw failure
            // The request may have committed even when its response never reached this device.
            try { endpoint().status(operation.secret).result() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw failure }
        }
    }
    private suspend fun clean(operation: DeletionOperation): DeletionOperation {
        if (auth.session.value.userOrNull?.uid == operation.uid) auth.signOut()
        if (operation.cleaned) return operation
        erase(operation.uid)
        return operation.copy(cleaned = true).also(store::save)
    }
}
