package com.firkat.fillin.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.profile.data.ProfileDeletionCoordinator
import com.firkat.fillin.feature.profile.domain.DeletionPhase
import com.firkat.fillin.feature.auth.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal enum class AccountConfirmationKind { SignOut, Delete }
internal data class AccountConfirmation(val uid: String, val kind: AccountConfirmationKind, val value: String = "")

class ProfileDeletionViewModel(val deletion: ProfileDeletionCoordinator, private val auth: AuthRepository) : ViewModel() {
    private val mutableConfirmation = MutableStateFlow<AccountConfirmation?>(null)
    internal val confirmation = mutableConfirmation.asStateFlow()
    val password = MutableStateFlow("")
    val googleBusy = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            auth.session.map { it.userOrNull?.uid }.distinctUntilChanged().collect { uid ->
                if (mutableConfirmation.value?.uid != uid) close()
            }
        }
    }

    fun open() = openConfirmation(AccountConfirmationKind.Delete)
    fun openSignOut() = openConfirmation(AccountConfirmationKind.SignOut)

    private fun openConfirmation(kind: AccountConfirmationKind) {
        if (mutableConfirmation.value != null || deletion.state.value.phase !in setOf(
                DeletionPhase.Idle,
                DeletionPhase.Completed,
            )) return
        val uid = auth.session.value.userOrNull?.uid ?: return
        mutableConfirmation.value = AccountConfirmation(uid, kind)
    }

    fun changeConfirmation(value: String) {
        val current = mutableConfirmation.value ?: return
        if (current.kind == AccountConfirmationKind.Delete) mutableConfirmation.value = current.copy(value = value)
    }

    fun close() { mutableConfirmation.value = null; password.value = "" }

    /** Consume before invoking the caller so repeated taps cannot dispatch twice. */
    fun confirmSignOut(onSignOut: () -> Unit) {
        val current = mutableConfirmation.value ?: return
        if (current.kind != AccountConfirmationKind.SignOut) return
        close()
        if (current.uid == auth.session.value.userOrNull?.uid) onSignOut()
    }

    fun submit() {
        val current = mutableConfirmation.value ?: return
        if (current.kind != AccountConfirmationKind.Delete || current.value.trim() != "УДАЛИТЬ") return
        close()
        if (current.uid == auth.session.value.userOrNull?.uid) deletion.submit(current.uid)
    }
    fun password() { val value = password.value; password.value = ""; deletion.confirmPassword(value) }
    fun google(launch: suspend () -> String) {
        if (googleBusy.value) return
        googleBusy.value = true
        viewModelScope.launch {
            try { deletion.confirmGoogle(launch()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: androidx.credentials.exceptions.GetCredentialCancellationException) { }
            catch (_: Exception) { deletion.authenticationFailed() }
            finally { googleBusy.value = false }
        }
    }
}
