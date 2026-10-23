package com.firkat.fillin.feature.budgetconnection

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.di.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.sync.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BudgetConnectionDeviceTest {
    @Test fun existingV4BudgetIsAttachedInPlaceAndUploadsOriginalQueuedRequest() = runBlocking {
        Environment().use { env ->
            val legacy = env.seedLegacy()
            val before = env.database(legacy) { it.budget().content()!! }
            val queued = before.commands.single()
            val budget = env.connection.budgets("owner").single()
            env.connection.connect("owner", budget)
            val cloud = env.manager.active.value!!.profile
            assertEquals(legacy.id, cloud.id)
            assertEquals(legacy.databaseName, cloud.databaseName)
            assertEquals(BudgetProfileKind.CLOUD, cloud.kind)
            assertEquals("owner", cloud.firebaseUid)
            assertEquals(before, env.database(cloud) { it.budget().content()!! }.let { it.copy(sync = before.sync) })
            env.manager.acquire(cloud.syncIdentity)!!.use { lease ->
                assertEquals(SyncResult.Completed, lease.scope.get<OperationsSynchronizer>().synchronize())
                assertTrue(lease.scope.get<BudgetStore>().pending().isEmpty())
            }
            assertEquals(listOf(queued.requestId), env.server.receipts.keys.toList())
            assertEquals(before.operations.single().id, env.server.entries.single().id)
            env.auth.logout()
            env.manager.restore()
            assertNotEquals(cloud.databaseName, env.manager.active.value!!.profile.databaseName)
            assertFalse(env.registry.available(null).contains(cloud))
            env.auth.login("owner")
            env.manager.restore()
            assertEquals(cloud, env.manager.active.value!!.profile)
            assertEquals(1, env.database(cloud) { it.budget().content()!!.operations.size })
        }
    }

    @Test fun foreignUidCannotListOrAttachTheOldBudget() = runBlocking {
        Environment().use { env ->
            val legacy = env.seedLegacy()
            val original = env.database(legacy) { it.budget().content()!! }
            val budget = env.server.summary()
            env.auth.login("other")
            env.manager.restore()
            assertTrue(env.connection.budgets("other").isEmpty())
            val failure = runCatching { env.connection.connect("other", budget) }.exceptionOrNull()
            assertEquals("access_denied", (failure as com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure).code)
            assertEquals(legacy, env.manager.active.value!!.profile)
            assertEquals(original, env.database(legacy) { it.budget().content()!! })
        }
    }

    @Test fun downloadingAnotherBudgetKeepsTheLocalBudgetAndUsesAnotherFile() = runBlocking {
        Environment().use { env ->
            val local = env.seedLegacy()
            val before = env.database(local) { it.budget().content()!! }
            env.server.replaceBudget()
            val budget = env.connection.budgets("owner").single()
            env.connection.connect("owner", budget)
            val cloud = env.manager.active.value!!.profile
            assertNotEquals(local.databaseName, cloud.databaseName)
            assertEquals(budget.id, env.database(cloud) { it.operations().state()!!.householdId })
            assertEquals(before, env.database(local) { it.budget().content()!! })
            env.auth.logout(); env.manager.restore()
            assertEquals(local, env.manager.active.value!!.profile)
        }
    }

    @Test fun failedInitialDownloadDoesNotSelectPartialDataAndRetryReusesProfile() = runBlocking {
        Environment().use { env ->
            val local = env.seedLegacy()
            env.server.replaceBudget()
            env.server.failSecondPage = true
            val budget = env.connection.budgets("owner").single()
            assertTrue(runCatching { env.connection.connect("owner", budget) }.isFailure)
            assertEquals(local, env.manager.active.value!!.profile)
            val profile = env.registry.available("owner").single { it.kind == BudgetProfileKind.CLOUD }
            assertFalse(env.database(profile) { it.operations().state()!!.localReady })
            env.server.failSecondPage = false
            env.connection.connect("owner", budget)
            assertEquals(profile, env.manager.active.value!!.profile)
            assertTrue(env.database(profile) { it.operations().state()!!.localReady })
            assertEquals(1, env.registry.available("owner").count { it.kind == BudgetProfileKind.CLOUD })
        }
    }

    @Test fun lostCommandResponseKeepsQueueAndRetryDoesNotDuplicateMoney() = runBlocking {
        Environment().use { env ->
            env.seedLegacy()
            env.connection.connect("owner", env.connection.budgets("owner").single())
            val cloud = env.manager.active.value!!.profile
            env.server.loseResponse = true
            env.manager.acquire(cloud.syncIdentity)!!.use { lease ->
                val sync = lease.scope.get<OperationsSynchronizer>()
                assertEquals(SyncResult.Retry, sync.synchronize())
                val pending = lease.scope.get<BudgetStore>().pending().single()
                assertNotNull(pending.wirePayload)
                assertEquals(1, env.server.entries.size)
                assertEquals(SyncResult.Completed, sync.synchronize())
                assertTrue(lease.scope.get<BudgetStore>().pending().isEmpty())
                assertEquals(1, env.server.entries.size)
                assertEquals(1, env.server.receipts.size)
            }
        }
    }

    internal class Environment(
        val server: Server = Server(),
        val registryName: String = "t05-registry-${UUID.randomUUID()}.db",
        val backend: String = "https://t05-${UUID.randomUUID()}.invalid/",
        private val deleteOnClose: Boolean = true,
        apiOverride: FirebaseBudgetApi? = null,
    ) : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val auth = TestAuth()
        private val registryDb = Room.databaseBuilder(context, BudgetProfileDatabase::class.java, registryName).build()
        val registry = RoomBudgetProfileRegistry(registryDb, backend)
        private val factory = BudgetDatabaseFactory(context)
        private val requests = FirebaseBudgetRequests(apiOverride ?: server, object : FirebaseIdTokenProvider {
            override fun isCurrentUser(uid: String) = (auth.session.value as? AuthSession.Authenticated)?.user?.uid == uid
            override suspend fun idToken(uid: String, forceRefresh: Boolean) = "synthetic-$uid"
        })
        private val remote = Retrofit.Builder().baseUrl("https://unused.invalid/")
            .client(OkHttpClient.Builder().addInterceptor { error("No real network in T05 tests") }.build()).build()
        private val application = koinApplication {
            androidContext(context)
            modules(budgetModule, module {
                single { Json { ignoreUnknownKeys = true; explicitNulls = false } }
                single { factory }
                single<BudgetProfileRegistry> { registry }
                single<AuthRepository> { auth }
                single { requests }
                single<com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadStore> { com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadStore(registryDb) }
                single { ActiveBudgetManager(get(), get(), getKoin(), cancelWork = {}) }
                single<OperationSyncScheduler> { OperationSyncScheduler {} }
                single<OperationConnectivity> { object : OperationConnectivity { override val online = MutableStateFlow(false) } }
                single<OperationSyncApi> { remote.create(OperationSyncApi::class.java) }
                single<OperationHistoryApi> { remote.create(OperationHistoryApi::class.java) }
                single<BudgetSyncApi> { remote.create(BudgetSyncApi::class.java) }
                single<CloudBudgetApi> { remote.create(CloudBudgetApi::class.java) }
                scope(BUDGET_SCOPE) { scoped<OperationSyncScheduler> { OperationSyncScheduler {} } }
            })
        }
        val manager: ActiveBudgetManager = application.koin.get()
        val koin get() = application.koin
        val connection = RoomBudgetConnectionRepository(requests, registry, factory, manager, OperationSyncScheduler {}, application.koin.get())

        val uploads: com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadStore get() = application.koin.get()
        val uploadConnection: com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadRepository get() = com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadRepository(auth, registry, uploads, factory, manager, OperationSyncScheduler {}, application.koin.get())
        val creation: BudgetCreationRepository get() = FirebaseBudgetCreationRepository(requests, RoomBudgetCreationStore(registryDb), connection)

        suspend fun seedLegacy(): BudgetProfile {
            auth.login("owner"); manager.restore()
            val profile = manager.active.value!!.profile
            manager.acquire(profile.syncIdentity)!!.use { lease ->
                val session = lease.scope.get<CloudBudgetSession>()
                session.createLocal("100.00".toBigDecimal(), session.today.minusDays(1))
                val db = lease.scope.get<OperationDatabase>()
                val content = db.budget().content()!!
                server.scope = SyncScopeDto(content.sync.householdId, content.sync.memberId, "OWNER", content.sync.accountingStartedAt!!, "Asia/Almaty", "KZT")
                server.categories = content.categories
                lease.scope.get<OperationHistoryStore>().create(NewOperation(OperationType.Expense,
                    content.categories.first { it.kind == "EXPENSE" }.id, "12.34".toBigDecimal(), session.today, null), null)
            }
            return profile
        }

        suspend fun <T> database(profile: BudgetProfile, block: suspend (OperationDatabase) -> T): T {
            val db = factory.open(profile)
            try { return block(db) } finally { db.close() }
        }

        override fun close() {
            val names = runBlocking { listOf(null, "owner", "other").flatMap { registry.available(it) }.map { it.databaseName }.distinct() }
            manager.close(); application.close(); registryDb.close()
            if (deleteOnClose) { names.forEach { context.deleteDatabase(it) }; context.deleteDatabase(registryName) }
        }
    }

    internal class TestAuth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Anonymous)
        fun login(uid: String) { session.value = AuthSession.Authenticated(AuthUser(uid, uid, null)) }
        fun logout() { session.value = AuthSession.Anonymous }
        override suspend fun signOut() = logout()
        override suspend fun signInWithGoogle(idToken: String) = error("No real login in T05 storage tests")
    }

    internal class Server : FirebaseBudgetApi {
        override suspend fun importLocal(authorization: String, request: ImportFirebaseBudgetDto): Response<AvailableBudgetDto> = error("Unused in T05/T06")
        var budgetExists = true
        var budgetName = "Тестовый бюджет"
        var openingBalance = "100.00"
        var loseCreationResponse = false
        val creations = linkedMapOf<String, Pair<CreateFirebaseBudgetDto, AvailableBudgetDto>>()
        override suspend fun create(authorization: String, request: CreateFirebaseBudgetDto): Response<AvailableBudgetDto> {
            check(authorization == "Bearer synthetic-owner")
            val saved = creations[request.requestId]
            if (saved != null && saved.first != request) return Response.error(409, "{}".toResponseBody())
            val budget = saved?.second ?: run {
                check(!budgetExists)
                replaceBudget()
                scope = scope.copy(accountingStartedAt = LocalDate.parse(request.accountingStartedOn).atStartOfDay(java.time.ZoneId.of("Asia/Almaty")).toInstant().toString())
                budgetExists = true; budgetName = request.name; openingBalance = request.openingBalance
                AvailableBudgetDto(scope.householdId, budgetName, scope.memberId, "OWNER").also { creations[request.requestId] = request to it }
            }
            if (loseCreationResponse) { loseCreationResponse = false; throw IOException("Synthetic lost creation response after commit") }
            return Response.success(budget)
        }
        var scope = newScope()
        var categories: List<LocalCategory> = seeds(scope)
        var failSecondPage = false
        var loseResponse = false
        val receipts = linkedMapOf<String, BudgetCommandReceipt>()
        val entries = mutableListOf<SyncOperationDto>()
        fun summary() = AvailableBudget(scope.householdId, budgetName, scope.memberId, scope.role)
        fun replaceBudget() { scope = newScope(); categories = seeds(scope); entries.clear(); receipts.clear() }
        override suspend fun budgets(authorization: String) = Response.success(if (budgetExists && authorization == "Bearer synthetic-owner") listOf(AvailableBudgetDto(scope.householdId, budgetName, scope.memberId, scope.role)) else emptyList())
        override suspend fun budget(authorization: String, budgetId: String): Response<CloudBudget> =
            if (authorization == "Bearer synthetic-owner" && budgetId == scope.householdId) Response.success(CloudBudget(scope)) else Response.error(403, "{}".toResponseBody())
        override suspend fun changes(authorization: String, budgetId: String, cursor: String?): Response<SyncPageDto> {
            check(authorization == "Bearer synthetic-owner" && budgetId == scope.householdId)
            val records = categories.mapIndexed { i, c -> SyncChangeDto(i + 1L, "CATEGORY", false, category = SyncCategoryDto(c.id, c.name, c.kind, c.iconCode, c.iconEmoji, false, c.dictionaryKey)) } +
                SyncChangeDto(categories.size + 1L, "BUDGET_SETTINGS", false, budgetSettings = SyncBudgetSettings(scope.householdId, openingBalance, "0.00")) +
                entries.mapIndexed { i, e -> SyncChangeDto(categories.size + 2L + i, "LEDGER", false, operation = e) }
            val after = cursor?.toLong() ?: 0L
            if (failSecondPage && after > 0) return Response.error(503, "{}".toResponseBody())
            val remaining = records.filter { it.revision > after }
            val page = if (failSecondPage) remaining.take(1) else remaining
            val more = page.size < remaining.size
            return Response.success(SyncPageDto(scope, page, (page.lastOrNull()?.revision ?: records.size.toLong()).toString(), more, records.size.toLong()))
        }
        override suspend fun command(authorization: String, budgetId: String, command: BudgetSyncCommand): Response<BudgetCommandReceipt> {
            check(authorization == "Bearer synthetic-owner" && budgetId == scope.householdId && command.householdId == budgetId && command.memberId == scope.memberId)
            val receipt = receipts.getOrPut(command.requestId) {
                check(command.type == "CREATE_OPERATION")
                entries += SyncOperationDto(command.entityId, command.categoryId!!, command.kind!!, command.amount!!, "KZT", command.amount,
                    command.occurredOn!!, Instant.now().toString(), "POSTED", 0, entries.size + 1L, requestId = command.requestId)
                BudgetCommandReceipt(command.requestId, categories.size + entries.size + 1L, 0)
            }
            if (loseResponse) { loseResponse = false; throw IOException("Synthetic lost response after commit") }
            return Response.success(receipt)
        }
        private fun newScope() = SyncScopeDto(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "OWNER", "2026-09-01T19:00:00Z", "Asia/Almaty", "KZT")
        private fun seeds(scope: SyncScopeDto) = listOf(LocalCategory(scope.key, UUID.randomUUID().toString(), "Продукты", "EXPENSE", "FOOD", "🍔", false, 0, "GROCERIES"))
    }
}
