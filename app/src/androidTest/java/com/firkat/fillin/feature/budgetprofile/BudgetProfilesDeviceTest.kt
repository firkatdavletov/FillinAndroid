package com.firkat.fillin.feature.budgetprofile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.di.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.categories.data.remote.CategoriesApi
import com.firkat.fillin.feature.categories.data.repository.OfflineCategoriesRepository
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.operationhistory.data.SyntheticSyncApi
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.sync.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import retrofit2.Retrofit
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BudgetProfilesDeviceTest {
    @Test fun legacyDatabaseIsRegisteredInPlaceWithUnchangedQueueAndCursor() = runBlocking {
        Environment().use { env ->
            val hash = MessageDigest.getInstance("SHA-256").digest(env.backend.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
            val original = BudgetProfile("legacy-$hash", "operations-$hash.db", BudgetProfileKind.LEGACY)
            val state = OperationSyncState(scopeKey = "household:member", householdId = "household", memberId = "member", cursor = "original-cursor", localReady = true, protocolVersion = 3)
            val queued = OperationOutbox("original-request", state.scopeKey, "original-operation", "{\"unchanged\":true}")
            env.factory.open(original).use { db ->
                db.operations().putState(state)
                db.operations().enqueue(queued)
            }
            assertEquals(original, env.registry.initialize())
            assertEquals(original, env.registry.initialize())
            assertEquals(1, env.registry.available(null).size)
            env.manager.restore()
            env.manager.acquire(original.syncIdentity)!!.use { lease ->
                val db = lease.scope.get<OperationDatabase>()
                assertEquals(state, db.operations().state())
                assertEquals(listOf(queued), db.operations().pending(state.scopeKey))
            }
        }
    }

    @Test fun twoProfilesKeepOperationsBothOutboxesAndRepositoriesSeparateAfterReopen() = runBlocking {
        Environment().use { env ->
            env.manager.restore()
            val first = env.manager.active.value!!.profile
            val firstSnapshot = env.seed(first, "100.00", "11.25")
            val second = env.registry.createLocal()
            val secondSnapshot = env.seed(second, "900.00", "22.50")
            assertNotEquals(first.databaseName, second.databaseName)
            assertNotEquals(firstSnapshot.householdId, secondSnapshot.householdId)
            assertNotEquals(firstSnapshot.operationIds, secondSnapshot.operationIds)
            assertNotEquals(firstSnapshot.budgetQueue, secondSnapshot.budgetQueue)
            // Each visit has closed the previous scope: Room must reload its original queue from disk.
            assertEquals(firstSnapshot, env.snapshot(first))
            assertEquals(secondSnapshot, env.snapshot(second))
            env.manager.acquire(second.syncIdentity)!!.use { b ->
                env.manager.select(first.id)
                env.manager.acquire(first.syncIdentity)!!.use { a ->
                    assertNotSame(a.scope.get<BudgetStore>(), b.scope.get<BudgetStore>())
                    assertNotSame(a.scope.get<OperationHistoryStore>(), b.scope.get<OperationHistoryStore>())
                    assertNotSame(a.scope.get<DashboardDataSource>(), b.scope.get<DashboardDataSource>())
                }
            }
        }
    }

    @Test fun cloudProfilesArePrivateToUidAndSelectionSurvivesRegistryReopen() = runBlocking {
        Environment().use { env ->
            env.auth.login("account-a")
            env.manager.restore()
            val cloud = env.registry.registerCloud("account-a", "budget-a", "OWNER")
            assertEquals(cloud, env.registry.registerCloud("account-a", "budget-a", "OWNER"))
            val snapshot = env.seed(cloud, "250.00", "10.00")
            env.auth.login("account-b")
            // Guard changes immediately, before the background auth observer processes the event.
            assertNull(env.manager.acquire(cloud.syncIdentity))
            env.manager.restore()
            assertFalse(env.registry.available("account-b").contains(cloud))
            try { env.manager.select(cloud.id); fail("Other account selected a private profile") } catch (_: IllegalArgumentException) { }
            env.auth.signOut()
            env.manager.restore()
            assertFalse(env.registry.available(null).contains(cloud))
            env.auth.login("account-a")
            env.manager.restore()
            assertEquals(cloud, env.manager.active.value!!.profile)
            assertEquals(snapshot, env.snapshot(cloud))
            // A fresh Room instance sees the persisted choice for each account.
            Room.databaseBuilder(env.context, BudgetProfileDatabase::class.java, env.registryName).build().use { reopened ->
                val registry = RoomBudgetProfileRegistry(reopened, env.backend)
                assertEquals(cloud, registry.selected("account-a"))
                assertEquals(BudgetProfileKind.LEGACY, registry.selected(null).kind)
            }
        }
    }

    @Test fun authObserverRestoresLocalProfileOnLogoutAndCloudOnReturn() = runBlocking {
        Environment().use { env ->
            env.auth.login("account-a")
            env.manager.restore()
            val cloud = env.registry.registerCloud("account-a", "budget-a", "MEMBER")
            env.manager.select(cloud.id)
            env.manager.start()
            env.auth.signOut()
            withTimeout(5000) { env.manager.active.filterNotNull().first { it.profile.kind == BudgetProfileKind.LEGACY } }
            env.auth.login("account-a")
            withTimeout(5000) { env.manager.active.filterNotNull().first { it.profile == cloud } }
            Unit
        }
    }

    @Test fun inFlightResponseKeepsItsOriginalDatabaseAcrossProfileSwitch() = runBlocking {
        Environment().use { env ->
            env.manager.restore()
            val first = env.manager.active.value!!.profile
            val second = env.registry.createLocal()
            val entered = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Unit>()
            val synthetic = SyntheticSyncApi()
            env.remote = object : OperationSyncApi by synthetic {
                override suspend fun changes(authorization: String, cursor: String?): SyncPageDto {
                    entered.complete(Unit)
                    response.await()
                    return synthetic.changes(authorization, cursor)
                }
            }
            val lease = env.manager.acquire(first.syncIdentity)!!
            val originalDb = lease.scope.get<OperationDatabase>()
            originalDb.operations().putState(OperationSyncState(scopeKey = synthetic.scope.key,
                householdId = synthetic.scope.householdId, memberId = synthetic.scope.memberId,
                localReady = true, cloudEnabled = true, cloudBound = true, protocolVersion = 3))
            val running = async(Dispatchers.IO) { lease.scope.get<OperationsSynchronizer>().synchronize() }
            withTimeout(5000) { entered.await() }
            env.manager.select(second.id)
            assertFalse(lease.scope.closed)
            response.complete(Unit)
            assertEquals(SyncResult.Completed, withTimeout(5000) { running.await() })
            assertEquals(synthetic.scope.key, originalDb.operations().state()!!.scopeKey)
            assertEquals(2, originalDb.budget().content()!!.categories.size)
            env.manager.acquire(second.syncIdentity)!!.use { assertNull(it.scope.get<OperationDatabase>().operations().state()) }
            lease.close()
            assertTrue(lease.scope.closed)
        }
    }

    @Test fun returningBeforeOldRequestFinishesReusesDatabaseAndMutex() = runBlocking {
        Environment().use { env ->
            env.manager.restore()
            val first = env.manager.active.value!!.profile
            val second = env.registry.createLocal()
            env.manager.acquire(first.syncIdentity)!!.use { old ->
                val sync = old.scope.get<OperationsSynchronizer>()
                val db = old.scope.get<OperationDatabase>()
                env.manager.select(second.id)
                env.manager.select(first.id)
                env.manager.acquire(first.syncIdentity)!!.use { current ->
                    assertSame(old.runtime, current.runtime)
                    assertSame(sync, current.scope.get<OperationsSynchronizer>())
                    assertSame(db, current.scope.get<OperationDatabase>())
                }
            }
        }
    }

    @Test fun workersRejectMissingProfileInactiveProfileAndWrongUid() = runBlocking {
        Environment().use { env ->
            env.auth.login("account-a")
            env.manager.restore()
            val cloud = env.registry.registerCloud("account-a", "budget-a", "OWNER")
            env.manager.select(cloud.id)
            val factory = OperationWorkerFactory(env.manager)
            suspend fun work(data: Data) = TestListenableWorkerBuilder<OperationSyncWorker>(env.context)
                .setWorkerFactory(factory).setInputData(data).build().doWork()
            assertEquals(ListenableWorker.Result.success(), work(Data.EMPTY))
            assertEquals(ListenableWorker.Result.success(), work(BudgetWorkIdentity.data(BudgetSyncIdentity(cloud.id, "account-b"))))
            assertEquals(ListenableWorker.Result.success(), work(BudgetWorkIdentity.data(BudgetSyncIdentity("missing-profile", "account-a"))))
            // Matching identity is routed to this profile and safely skips a disabled local queue.
            assertEquals(ListenableWorker.Result.success(), work(BudgetWorkIdentity.data(cloud.syncIdentity)))
            env.auth.signOut()
            assertEquals(ListenableWorker.Result.success(), work(BudgetWorkIdentity.data(cloud.syncIdentity)))
            assertNotEquals(BudgetWorkIdentity.uniqueName(cloud.syncIdentity), BudgetWorkIdentity.uniqueName(BudgetSyncIdentity(cloud.id, "account-b")))
            assertEquals(cloud.syncIdentity, BudgetWorkIdentity.read(BudgetWorkIdentity.data(cloud.syncIdentity)))
        }
    }

    internal data class Snapshot(val householdId: String, val operationIds: List<String>, val budgetQueue: List<com.firkat.fillin.core.database.BudgetOutbox>, val oldQueue: List<OperationOutbox>, val records: List<com.firkat.fillin.core.database.LocalSyncRecord>)

    internal class TestAuth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Anonymous)
        fun login(uid: String) { session.value = AuthSession.Authenticated(AuthUser(uid, "Synthetic", null)) }
        override suspend fun signInWithGoogle(idToken: String) = error("No real authentication in these tests")
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
    }

    internal class Environment : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registryName = "t04-registry-${UUID.randomUUID()}.db"
        val backend = "https://t04-${UUID.randomUUID()}.invalid/"
        private val registryDb = Room.databaseBuilder(context, BudgetProfileDatabase::class.java, registryName).build()
        val registry = RoomBudgetProfileRegistry(registryDb, backend)
        val factory = BudgetDatabaseFactory(context)
        val auth = TestAuth()
        var remote: OperationSyncApi? = null
        private val noNetwork = Retrofit.Builder().baseUrl("https://unused.invalid/")
            .client(OkHttpClient.Builder().addInterceptor { error("Unexpected network access in T04 test") }.build()).build()
        private val application = koinApplication {
            androidContext(context)
            modules(budgetModule, module {
                single { Json { ignoreUnknownKeys = true; explicitNulls = false } }
                single { factory }
                single { registryDb }
                single<BudgetProfileRegistry> { registry }
                single<AuthRepository> { auth }
                single { ActiveBudgetManager(get(), get(), getKoin(), cancelWork = {}) }
                single<OperationConnectivity> { object : OperationConnectivity { override val online = MutableStateFlow(false) } }
                single<OperationSyncApi> { remote ?: noNetwork.create(OperationSyncApi::class.java) }
                single<OperationHistoryApi> { noNetwork.create(OperationHistoryApi::class.java) }
                single<CloudBudgetApi> { noNetwork.create(CloudBudgetApi::class.java) }
                single<BudgetSyncApi> { noNetwork.create(BudgetSyncApi::class.java) }
                // Device isolation tests must never enqueue work against the user's production budget.
                scope(BUDGET_SCOPE) {
                    scoped<OperationSyncScheduler> { OperationSyncScheduler {} }
                    // These T04 tests isolate storage/lifetime from the Firebase access policy tested in T05.
                    scoped<CloudBudgetApi> { getKoin().get<CloudBudgetApi>() }
                    scoped { CloudBudgetSession(get(), get(), "synthetic-test-token", get(), get(), checkAccess = {
                        if (!get<ActiveBudgetManager>().isCurrent(get<BudgetProfile>())) throw CloudDisabledException()
                    }) }
                    // Historical transport is a fixture for lifetime/routing tests, never production credentials.
                    scoped { OperationsSynchronizer(get(), get(), "synthetic-test-token", get(), budget = get(),
                        budgetApi = get(), cloud = get(), cloudApi = get(),
                        canSynchronize = { get<ActiveBudgetManager>().isCurrent(get<BudgetProfile>()) }) }
                }
            })
        }
        val manager: ActiveBudgetManager = application.koin.get()

        suspend fun seed(profile: BudgetProfile, balance: String, expense: String): Snapshot {
            manager.select(profile.id)
            manager.acquire(profile.syncIdentity)!!.use { lease ->
                val db = lease.scope.get<OperationDatabase>()
                val session = lease.scope.get<CloudBudgetSession>()
                session.createLocal(BigDecimal(balance), session.today.minusDays(1))
                val category = db.budget().content()!!.categories.first { it.kind == "EXPENSE" }
                lease.scope.get<OperationHistoryStore>().create(NewOperation(OperationType.Expense, category.id, BigDecimal(expense), session.today, null), null)
                OfflineCategoriesRepository(lease.scope.get()).createCategory("Тест", CategoryKind.Expense, "HOME")
                val state = db.operations().state()!!
                db.operations().enqueue(OperationOutbox("legacy-${profile.id}", state.scopeKey, "operation", "{\"keepPayload\":true}"))
            }
            return snapshot(profile)
        }

        suspend fun snapshot(profile: BudgetProfile): Snapshot {
            manager.select(profile.id)
            return manager.acquire(profile.syncIdentity)!!.use { lease ->
                val db = lease.scope.get<OperationDatabase>()
                val content = db.budget().content()!!
                Snapshot(content.sync.householdId, content.operations.map { it.id }, lease.scope.get<BudgetStore>().pending(), db.operations().pending(content.sync.scopeKey), db.budget().content()!!.records)
            }
        }

        override fun close() {
            val names = runBlocking { listOf(null, "account-a", "account-b").flatMap { registry.available(it) }.map { it.databaseName }.distinct() }
            manager.close()
            application.close()
            registryDb.close()
            names.forEach { context.deleteDatabase(it) }
            context.deleteDatabase(registryName)
        }
    }
}

private inline fun <T : androidx.room.RoomDatabase, R> T.use(block: (T) -> R): R = try { block(this) } finally { close() }
