package com.firkat.fillin.feature.sync.data

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency

import androidx.room.withTransaction
import com.firkat.fillin.core.database.LocalSyncRecord
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.SyncBlockedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class CloudDisabledException : Exception()

/** Owns the first-launch transaction and the durable opt-in to all network work. */
class CloudBudgetSession(
    private val database: OperationDatabase,
    private val api: CloudBudgetApi,
    private val apiToken: String,
    private val json: Json,
    private val scheduler: OperationSyncScheduler,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
    private val checkAccess: () -> Unit = {},
    val firebase: Boolean = false,
) : com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository,
    com.firkat.fillin.feature.sync.domain.CloudSyncSettings {
    private val mutex = Mutex()
    private val dao get() = database.operations()
    override val today: LocalDate get() = LocalDate.now(clock.withZone(ZONE))
    override val ready = dao.observeContent().map { it?.sync?.let { s -> s.localReady || s.initialSyncComplete } == true }.distinctUntilChanged()

    override suspend fun createLocal(
        openingBalance: BigDecimal,
        startedOn: LocalDate,
    ) = createLocal(openingBalance, startedOn, BudgetCurrency.KZT)

    suspend fun createLocal(openingBalance: BigDecimal, startedOn: LocalDate, currency: BudgetCurrency) = mutex.withLock {
        require(openingBalance.scale() <= 2 && openingBalance.abs() <= BigDecimal("99999999999999999.99"))
        require(startedOn <= today && startedOn.year >= 1)
        database.withTransaction {
            check(dao.state()?.let { it.localReady || it.initialSyncComplete } != true)
            val household = UUID.randomUUID().toString()
            val member = UUID.randomUUID().toString()
            val scope = "$household:$member"
            val seeds =
                listOf(
                    seed("Продукты", "EXPENSE", "FOOD", "GROCERIES"),
                    seed("Здоровье", "EXPENSE", "HEALTH", "HEALTH"),
                    seed("Зарплата", "INCOME", "INCOME", "SALARY"),
                    seed("Другое", "EXPENSE", "OTHER", "OTHER"),
                )
            val request = CreateCloudBudget(UUID.randomUUID().toString(), household, member, openingBalance.setScale(2).toPlainString(), startedOn.toString(), seeds, currency.name.takeUnless { currency == BudgetCurrency.KZT })
            seeds.forEach { c ->
                dao.putCategory(
                    LocalCategory(
                        scope,
                        c.id,
                        c.name,
                        c.kind,
                        c.icon,
                        "📦",
                        false,
                        0,
                        c.dictionaryKey,
                    ),
                )
            }
            database.budget().putRecord(LocalSyncRecord(scope, "BUDGET_SETTINGS", household, json.encodeToString(SyncBudgetSettings(household, request.openingBalance, "0.00")), 0))
            database.budget().clearStaged()
            dao.putState(
                OperationSyncState(
                    scopeKey = scope,
                    householdId = household,
                    memberId = member,
                    accountingStartedAt = startedOn.atStartOfDay(ZONE).toInstant().toString(),
                    role = "OWNER",
                    protocolVersion = 4,
                    localReady = true,
                    bootstrapPayload = json.encodeToString(request),
                    currency = currency.name,
                ),
            )
        }
    }

    override suspend fun connectExisting() =
        mutex.withLock {
            checkAccess()
            requireToken()
            check(dao.state()?.let { it.localReady || it.initialSyncComplete } != true)
            val original = dao.state() ?: OperationSyncState()
            try {
                val remote = body(api.budget(authorization()))
                validate(remote)
                database.withTransaction {
                    database.budget().clearStaged()
                    dao.putState(
                        OperationSyncState(
                            scopeKey = remote.scope.key,
                            householdId = remote.scope.householdId,
                            memberId = remote.scope.memberId,
                            accountingStartedAt = remote.scope.accountingStartedAt,
                            role = remote.scope.role,
                            protocolVersion = 4,
                            cloudEnabled = true,
                            cloudBound = true,
                        ),
                    )
                }
                val storage = RoomOperationSyncStorage(database, json, publishReady = false)
                do {
                    checkAccess()
                    val previous = storage.state()
                    val page = api.changes(authorization(), previous.cursor)
                    require(page.scope.copy(currency = remote.scope.currency, currencyVersion = remote.scope.currencyVersion) == remote.scope && page.nextCursor.isNotBlank() && (!page.hasMore || page.nextCursor != previous.cursor))
                    storage.apply(page)
                } while (page.hasMore)
                withContext(NonCancellable) {
                    database.withTransaction {
                        val complete = requireNotNull(dao.state())
                        dao.putState(complete.copy(localReady = true, initialSyncComplete = true, lastSyncedAt = clock.instant().toString(), errorCode = null))
                    }
                }
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    database.withTransaction {
                        database.budget().clearStaged()
                        dao.putState(original)
                    }
                }
                throw e
            }
        }

    override suspend fun enable() {
        checkAccess()
        requireToken()
        database.withTransaction {
            val state = requireNotNull(dao.state())
            check(state.localReady || state.initialSyncComplete)
            dao.putState(state.copy(cloudEnabled = true, errorCode = null))
        }
        scheduler.schedule()
    }

    override suspend fun disable() {
        database.withTransaction { dao.state()?.let { dao.putState(it.copy(cloudEnabled = false)) } }
    }

    suspend fun enabled(): Boolean = dao.state()?.let { it.cloudEnabled && (it.localReady || it.initialSyncComplete) } == true

    suspend fun checkEnabled() {
        checkAccess()
        if (!enabled()) throw CloudDisabledException()
    }

    suspend fun ensureBound() =
        mutex.withLock {
            checkEnabled()
            val state = requireNotNull(dao.state())
            if (state.cloudBound) return@withLock
            val payload = requireNotNull(state.bootstrapPayload)
            try {
                val remote = body(api.create(authorization(), json.decodeFromString(payload)))
                validate(remote)
                if (remote.scope.key != state.scopeKey) throw SyncBlockedException("scope_changed")
                database.withTransaction {
                    val latest = requireNotNull(dao.state())
                    dao.putState(latest.copy(cloudBound = true))
                }
            } catch (e: SyncBlockedException) {
                if (e.code == "budget_already_exists") {
                    database.withTransaction {
                        dao.state()?.let { dao.putState(it.copy(cloudEnabled = false)) }
                    }
                }
                throw e
            }
        }

    private fun validate(budget: CloudBudget) {
        UUID.fromString(budget.scope.householdId)
        UUID.fromString(budget.scope.memberId)
        require(BudgetCurrency.entries.any { it.name == budget.scope.currency } && budget.scope.timeZone == "Asia/Almaty")
    }

    private fun requireToken() {
        if (!firebase && apiToken.isBlank()) throw SyncBlockedException("missing_api_token")
    }

    private fun authorization() = "Bearer $apiToken"

    private fun seed(
        name: String,
        kind: String,
        icon: String,
        key: String,
    ) = CloudSeedCategory(UUID.randomUUID().toString(), name, kind, icon, key)

    private fun <T> body(response: Response<T>): T {
        if (response.isSuccessful) return response.body() ?: throw IOException("Empty budget response")
        val code =
            when (response.code()) {
                401 -> "unauthorized"
                403 -> "access_denied"
                404 -> "budget_not_found"
                409 -> response.errorBody()?.string()?.let { runCatching { json.decodeFromString<com.firkat.fillin.feature.operationhistory.data.remote.SyncApiError>(it).code }.getOrNull() } ?: "budget_already_exists"
                else -> throw HttpException(response)
            }
        throw SyncBlockedException(code)
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Almaty")
    }
}
