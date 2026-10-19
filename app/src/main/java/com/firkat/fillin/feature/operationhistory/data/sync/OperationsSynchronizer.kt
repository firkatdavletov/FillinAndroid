package com.firkat.fillin.feature.operationhistory.data.sync

import com.firkat.fillin.feature.operationhistory.data.local.OperationOutbox
import com.firkat.fillin.feature.operationhistory.data.local.OperationSyncState
import com.firkat.fillin.feature.operationhistory.data.remote.CreateOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.CreatedOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import com.firkat.fillin.feature.operationhistory.data.remote.SyncApiError
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.sync.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock

interface OperationSyncStorage {
    suspend fun state(): OperationSyncState

    suspend fun setRunning(running: Boolean)

    suspend fun apply(page: SyncPageDto)

    suspend fun pending(): List<OperationOutbox>

    suspend fun acknowledge(response: CreatedOperationDto)

    suspend fun reject(
        command: OperationOutbox,
        errorCode: String,
    )

    suspend fun finish(
        errorCode: String?,
        syncedAt: String? = null,
    )
}

enum class SyncResult { Completed, Retry, Blocked }

class SyncBlockedException(
    val code: String,
) : Exception()

/** All entry points share this mutex; the database outbox is the durable queue. */
class OperationsSynchronizer(
    private val api: OperationSyncApi,
    private val storage: OperationSyncStorage,
    private val apiToken: String,
    private val json: Json,
    private val clock: Clock = Clock.systemUTC(),
    private val budget: BudgetStore? = null,
    private val budgetApi: BudgetSyncApi? = null,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    suspend fun synchronize(): SyncResult =
        mutex.withLock {
            if (apiToken.isBlank()) {
                storage.finish("missing_api_token")
                return@withLock SyncResult.Blocked
            }
            _running.value = true
            try {
                storage.setRunning(true)
                pull()
                val budgetCommands = budget?.pending().orEmpty()
                pushBudget(budgetCommands, deferDeletes = true)
                val pendingCommands = storage.pending()
                for (command in pendingCommands) {
                    val request = json.decodeFromString<CreateOperationDto>(command.payload)
                    if (budget != null && !budget.operationCategoryReady(request.categoryId)) continue
                    val state = storage.state()
                    if (command.scopeKey != state.scopeKey || request.householdId != state.householdId ||
                        request.memberId != state.memberId || request.requestId != command.requestId
                    ) {
                        throw SyncBlockedException("scope_changed")
                    }
                    val response = api.create("Bearer $apiToken", request)
                    if (response.isSuccessful) {
                        val body = response.body() ?: throw IOException("Invalid operation response")
                        if (body.requestId != command.requestId || body.operation.requestId != command.requestId) {
                            throw IOException("Invalid operation identity")
                        }
                        storage.acknowledge(body)
                    } else {
                        val error =
                            response.errorBody()?.string()?.let {
                                runCatching { json.decodeFromString<SyncApiError>(it) }.getOrNull()
                            }
                        when {
                            response.code() == 401 -> {
                                throw SyncBlockedException("unauthorized")
                            }

                            response.code() == 403 -> {
                                throw SyncBlockedException("access_denied")
                            }

                            response.code() == 409 -> {
                                throw SyncBlockedException(error?.code ?: "conflict")
                            }

                            response.code() in listOf(400, 422) &&
                                error?.code in setOf("invalid_operation", "invalid_request") -> {
                                storage.reject(command, error?.reason ?: requireNotNull(error).code)
                            }

                            else -> {
                                throw HttpException(response)
                            }
                        }
                    }
                }
                pushBudget(
                    budgetCommands.filter {
                        json.decodeFromString<BudgetSyncCommand>(it.intent).type ==
                            "DELETE_CATEGORY"
                    },
                    deferDeletes = false,
                )
                if (pendingCommands.isNotEmpty() || budgetCommands.isNotEmpty()) pull()
                storage.finish(null, clock.instant().toString())
                SyncResult.Completed
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SyncBlockedException) {
                storage.finish(exception.code)
                SyncResult.Blocked
            } catch (exception: HttpException) {
                val code =
                    when (exception.code()) {
                        401 -> "unauthorized"
                        403 -> "access_denied"
                        409 -> "scope_changed"
                        else -> "server_unavailable"
                    }
                storage.finish(code)
                if (exception.code() in listOf(401, 403, 409)) SyncResult.Blocked else SyncResult.Retry
            } catch (exception: IOException) {
                storage.finish("network")
                SyncResult.Retry
            } catch (exception: SerializationException) {
                storage.finish("invalid_response")
                SyncResult.Retry
            } catch (exception: IllegalArgumentException) {
                storage.finish("invalid_response")
                SyncResult.Retry
            } finally {
                _running.value = false
                withContext(NonCancellable) { storage.setRunning(false) }
            }
        }

    private suspend fun pushBudget(
        commands: List<com.firkat.fillin.core.database.BudgetOutbox>,
        deferDeletes: Boolean,
    ) {
        val store = budget ?: return
        val remote = budgetApi ?: return
        for (item in commands) {
            if (deferDeletes &&
                json.decodeFromString<BudgetSyncCommand>(item.intent).type == "DELETE_CATEGORY"
            ) {
                continue
            }
            val command = store.prepare(item) ?: continue
            val state = storage.state()
            if (item.scopeKey != state.scopeKey || command.householdId != state.householdId ||
                command.memberId != state.memberId
            ) {
                throw SyncBlockedException("scope_changed")
            }
            val response = remote.command("Bearer $apiToken", command)
            if (response.isSuccessful) {
                store.acknowledge(item, response.body() ?: throw IOException("Invalid command response"))
            } else {
                val error =
                    response.errorBody()?.string()?.let {
                        runCatching { json.decodeFromString<SyncApiError>(it) }.getOrNull()
                    }
                when {
                    response.code() == 401 -> {
                        throw SyncBlockedException("unauthorized")
                    }

                    response.code() == 403 -> {
                        throw SyncBlockedException("access_denied")
                    }

                    response.code() == 409 && error?.code in
                        setOf(
                            "scope_changed",
                            "idempotency_conflict",
                        )
                    -> {
                        throw SyncBlockedException(requireNotNull(error).code)
                    }

                    response.code() == 409 && error != null -> {
                        store.fail(item, "CONFLICT", error.code)
                    }

                    response.code() in setOf(400, 404, 422) && error != null -> {
                        store.fail(
                            item,
                            "REJECTED",
                            error.reason ?: error.code,
                        )
                    }

                    else -> {
                        throw HttpException(response)
                    }
                }
            }
        }
    }

    private suspend fun pull() {
        do {
            val before = storage.state()
            val page = api.changes("Bearer $apiToken", before.cursor)
            require(page.nextCursor.isNotBlank() && (!page.hasMore || page.nextCursor != before.cursor))
            storage.apply(page)
        } while (page.hasMore)
    }
}

fun interface OperationSyncScheduler {
    fun schedule()
}
