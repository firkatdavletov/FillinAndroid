package com.firkat.fillin.feature.budgetprofile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.scope.UnboundKoinScope
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI

/** A separate ViewModelStore prevents an old screen from retaining another budget's repositories. */
@OptIn(KoinExperimentalAPI::class, KoinDelicateAPI::class)
@Composable
fun ActiveBudgetContent(
    manager: ActiveBudgetManager = koinInject(),
    auth: AuthRepository = koinInject(),
    content: @Composable () -> Unit,
) {
    val runtime by manager.active.collectAsStateWithLifecycle()
    val session by auth.session.collectAsStateWithLifecycle()
    val failed by manager.failed.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val uid = (session as? AuthSession.Authenticated)?.user?.uid
    val visible = runtime?.takeIf { it.profile.accessibleTo(uid) }
    if (visible == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (failed) {
                Text(stringResource(R.string.budget_profile_open_failed))
                Button(onClick = { coroutineScope.launch { manager.restore() } }) { Text(stringResource(R.string.budget_profile_retry)) }
            } else CircularProgressIndicator()
        }
        return
    }
    key(visible.scope.id) {
        val lease = remember(visible) { visible.retain() }
        if (lease != null) {
            val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
            DisposableEffect(lease) {
                onDispose {
                    owner.viewModelStore.clear()
                    lease.close()
                }
            }
            UnboundKoinScope(lease.scope) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
            }
        }
    }
}
