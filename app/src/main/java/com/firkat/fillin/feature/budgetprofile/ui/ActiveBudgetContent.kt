package com.firkat.fillin.feature.budgetprofile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetRuntime
import com.firkat.fillin.feature.budgetprofile.data.BudgetRuntimeLease
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.sync.data.BudgetStore
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.scope.UnboundKoinScope
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI

internal val LocalBudgetCurrency = staticCompositionLocalOf { BudgetCurrency.KZT }

/** Keeps Main's budget models alive while an Auth entry covers it, and across rotation. */
class MainBudgetViewModelStore :
    ViewModel(),
    ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var lease by mutableStateOf<BudgetRuntimeLease?>(null)

    fun isBoundTo(
        runtime: BudgetRuntime
    ): Boolean = lease?.runtime === runtime

    fun bind(
        runtime: BudgetRuntime?
    ) {
        if (lease?.runtime === runtime) return
        viewModelStore.clear()
        lease?.close()
        lease = runtime?.retain()
    }

    override fun onCleared() {
        viewModelStore.clear()
        lease?.close()
        lease = null
    }
}

/** A separate ViewModelStore prevents an old screen from retaining another budget's repositories. */
@OptIn(KoinExperimentalAPI::class, KoinDelicateAPI::class)
@Composable
fun ActiveBudgetContent(
    manager: ActiveBudgetManager = koinInject(),
    auth: AuthRepository = koinInject(),
    viewModelStoreOwner: MainBudgetViewModelStore? = null,
    content: @Composable () -> Unit
) {
    val parentOwner = LocalViewModelStoreOwner.current as? HasDefaultViewModelProviderFactory
    val runtime by manager.active.collectAsStateWithLifecycle()
    val session by auth.session.collectAsStateWithLifecycle()
    val failed by manager.failed.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val uid = (session as? AuthSession.Authenticated)?.user?.uid
    val visible = runtime?.takeIf { it.profile.accessibleTo(uid) }
    if (visible == null || (viewModelStoreOwner != null && !viewModelStoreOwner.isBoundTo(visible))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (failed) {
                Text(stringResource(R.string.budget_profile_open_failed))
                Button(onClick = {
                    coroutineScope.launch { manager.restore() }
                }) { Text(stringResource(R.string.budget_profile_retry)) }
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }
    key(visible.scope.id) {
        val lease = remember(visible) { visible.retain() }
        if (lease != null) {
            val owner =
                viewModelStoreOwner ?: remember {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                }
            DisposableEffect(lease, owner) {
                onDispose {
                    if (viewModelStoreOwner == null) owner.viewModelStore.clear()
                    lease.close()
                }
            }
            // Budget models keep their own store while using the enclosing Android saved-state registry.
            val stateOwner =
                remember(owner, parentOwner) {
                    if (parentOwner == null) {
                        owner
                    } else {
                        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
                            override val viewModelStore get() = owner.viewModelStore
                            override val defaultViewModelProviderFactory get() = parentOwner.defaultViewModelProviderFactory
                            override val defaultViewModelCreationExtras get() = parentOwner.defaultViewModelCreationExtras
                        }
                    }
                }
            UnboundKoinScope(lease.scope) {
                val store = remember(lease) { lease.scope.get<BudgetStore>() }
                val currency by store.currency.collectAsStateWithLifecycle(initialValue = BudgetCurrency.KZT)
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides stateOwner,
                    LocalBudgetCurrency provides currency,
                    content = content
                )
            }
        }
    }
}
