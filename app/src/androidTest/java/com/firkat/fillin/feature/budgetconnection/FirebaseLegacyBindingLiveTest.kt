package com.firkat.fillin.feature.budgetconnection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.ServerAuthRepository
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.android.ext.android.getKoin

/** Explicit operator preparation only; reads budget metadata and never sends a financial command. */
class FirebaseLegacyBindingLiveTest {
    @Test fun prepareVerifiedCurrentAccountAndExistingMemberConfiguration() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("prepareLegacyBinding") == "true")
        val context = ApplicationProvider.getApplicationContext<com.firkat.fillin.FillinApplication>()
        val koin = context.getKoin()
        val session =
            withTimeout(5000) {
                koin.get<AuthRepository>().session.filterIsInstance<AuthSession.Authenticated>().first()
            }
        val manager = koin.get<ActiveBudgetManager>()
        manager.restore()
        val runtime = requireNotNull(manager.active.value)
        assertTrue(runtime.profile.kind == BudgetProfileKind.LEGACY)
        runtime.retain()!!.use { lease ->
            val state = requireNotNull(lease.scope.get<OperationDatabase>().operations().state())
            assertTrue(state.cloudBound && state.initialSyncComplete)
            // Export explicit cached IDs after verifying Google identity through Firebase.
            // The server validates the configured member independently when the binding is enabled.
            val verified = koin.get<ServerAuthRepository>().authenticate(session.user.uid)
            assertTrue(verified.firebaseUid == session.user.uid)
            java.util.UUID.fromString(state.householdId)
            java.util.UUID.fromString(state.memberId)
            assertTrue(
                (koin.get<AuthRepository>().session.value as? AuthSession.Authenticated)?.user?.uid == session.user.uid
            )
            context.openFileOutput("t05-legacy-binding.env", Context.MODE_PRIVATE).bufferedWriter().use {
                it.write("FIREBASE_LEGACY_UID=${session.user.uid}\nFIREBASE_LEGACY_MEMBER_ID=${state.memberId}\n")
            }
        }
    }
}
