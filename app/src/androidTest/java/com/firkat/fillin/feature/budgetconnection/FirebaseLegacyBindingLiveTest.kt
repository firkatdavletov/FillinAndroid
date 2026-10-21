package com.firkat.fillin.feature.budgetconnection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.sync.data.CloudBudgetApi
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
        val session = withTimeout(5000) { koin.get<AuthRepository>().session.filterIsInstance<AuthSession.Authenticated>().first() }
        val manager = koin.get<ActiveBudgetManager>()
        manager.restore()
        val runtime = requireNotNull(manager.active.value)
        assertTrue(runtime.profile.kind == BudgetProfileKind.LEGACY)
        runtime.retain()!!.use { lease ->
            val state = requireNotNull(lease.scope.get<OperationDatabase>().operations().state())
            assertTrue(state.cloudBound && state.initialSyncComplete)
            // Confirm the cached member is still the member of the configured existing server budget.
            val response = koin.get<CloudBudgetApi>().budget("Bearer ${BuildConfig.MOBILE_API_TOKEN}")
            assertTrue(response.isSuccessful)
            val scope = requireNotNull(response.body()).scope
            assertTrue(scope.householdId == state.householdId && scope.memberId == state.memberId)
            assertTrue((koin.get<AuthRepository>().session.value as? AuthSession.Authenticated)?.user?.uid == session.user.uid)
            context.openFileOutput("t05-legacy-binding.env", Context.MODE_PRIVATE).bufferedWriter().use {
                it.write("FIREBASE_LEGACY_UID=${session.user.uid}\nFIREBASE_LEGACY_MEMBER_ID=${scope.memberId}\n")
            }
        }
    }
}
