package com.firkat.fillin.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.launch.domain.LocalBudgetRestorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppNavigationViewModel(
    private val auth: AuthRepository,
    private val localBudget: LocalBudgetRestorer,
) : ViewModel() {
    // Activity ownership keeps navigation on rotation; a new process starts at Launch.
    private val entries = mutableStateListOf<AppRoute>(AppRoute.Launch)
    val backStack: List<AppRoute> get() = entries
    private val failure = MutableStateFlow(false)
    val launchFailed = failure.asStateFlow()
    private val retries = MutableStateFlow(0)
    private var observer: Job? = null
    private var lastUid: String? = null

    /** Called after Launch has entered composition, even for an immediately restored user. */
    fun onLaunchShown() {
        if (observer != null) return
        observer = viewModelScope.launch {
            combine(auth.session, retries) { session, _ -> session }.collectLatest { session ->
                when (session) {
                    AuthSession.Loading -> Unit
                    is AuthSession.Authenticated -> {
                        lastUid = session.user.uid
                        failure.value = false
                        openMain()
                    }
                    AuthSession.Anonymous, AuthSession.Unavailable -> {
                        if (lastUid != null) {
                            lastUid = null
                            replaceWith(AppRoute.Auth)
                        } else if (entries.last() == AppRoute.Launch) {
                            failure.value = false
                            try {
                                replaceWith(if (localBudget.restoreCreatedBudget()) AppRoute.Main else AppRoute.Auth)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                failure.value = true
                            }
                        }
                    }
                }
            }
        }
    }

    fun retryLaunch() { retries.update { it + 1 } }

    fun openAuthentication() {
        if (entries.last() == AppRoute.Main && auth.session.value !is AuthSession.Authenticated) {
            entries.add(AppRoute.Auth)
        }
    }

    fun continueAsGuest() {
        if (entries.last() == AppRoute.Auth) openMain()
    }

    fun back() {
        if (entries.size > 1) entries.removeAt(entries.lastIndex)
    }

    private fun openMain() {
        if (AppRoute.Main in entries) {
            while (entries.last() != AppRoute.Main) entries.removeAt(entries.lastIndex)
        } else replaceWith(AppRoute.Main)
    }

    private fun replaceWith(route: AppRoute) {
        entries.clear()
        entries.add(route)
    }
}
