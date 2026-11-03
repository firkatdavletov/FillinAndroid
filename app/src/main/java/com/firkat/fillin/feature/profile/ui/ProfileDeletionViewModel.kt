package com.firkat.fillin.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.profile.data.ProfileDeletionCoordinator
import com.firkat.fillin.feature.auth.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class ProfileDeletionViewModel(val deletion: ProfileDeletionCoordinator, private val auth: AuthRepository) : ViewModel() {
    val confirmation = MutableStateFlow<String?>(null)
    val password = MutableStateFlow("")
    val googleBusy = MutableStateFlow(false)
    fun open() { confirmation.value = "" }
    fun close() { confirmation.value = null; password.value = "" }
    fun submit() {
        if (confirmation.value?.trim() != "УДАЛИТЬ") return
        val uid = auth.session.value.userOrNull?.uid ?: return
        close(); deletion.submit(uid)
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
