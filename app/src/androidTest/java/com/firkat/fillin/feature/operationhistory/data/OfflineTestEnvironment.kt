package com.firkat.fillin.feature.operationhistory.data

import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.local.RoomOperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.remote.CreateOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.CreatedOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.OperationSyncApi
import com.firkat.fillin.feature.operationhistory.data.remote.SyncCategoryDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncChangeDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncScopeDto
import com.firkat.fillin.feature.operationhistory.data.repository.OfflineOperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.data.sync.OperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationsSynchronizer
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal class SyntheticConnectivity : OperationConnectivity {
    override val online = MutableStateFlow(true)
}

internal class OfflineTestEnvironment(
    database: OperationDatabase,
    val api: SyntheticSyncApi,
) {
    val connectivity = SyntheticConnectivity()
    val json = Json { ignoreUnknownKeys = true }
    val storage = RoomOperationSyncStorage(database)
    val synchronizer = OperationsSynchronizer(api, storage, "synthetic-test-token", json, CLOCK)
    var scheduled = 0
    val repository =
        OfflineOperationHistoryRepository(
            database,
            object : OperationHistoryRepository {
                override suspend fun getOperations(
                    limit: Int,
                    cursor: Long?,
                    filter: OperationHistoryFilter,
                ): OperationPage = error("Unexpected network read")

                override suspend fun updatePurchase(
                    id: String,
                    version: Long,
                    amount: BigDecimal,
                ): Operation = error("Unexpected online mutation")

                override suspend fun refundPurchase(
                    id: String,
                    version: Long,
                ) {
                    error("Unexpected online mutation")
                }
            },
            synchronizer,
            OperationSyncScheduler { scheduled++ },
            connectivity,
            json,
            CLOCK,
        )

    companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-18T05:00:00Z"), ZoneId.of("Asia/Almaty"))
    }
}

internal class SyntheticSyncApi : OperationSyncApi {
    var scope =
        SyncScopeDto(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "OWNER",
            "2026-09-01T19:00:00Z",
            "Asia/Almaty",
            "KZT",
        )
    val expenseCategory = SyncCategoryDto(UUID.randomUUID().toString(), "Продукты", "EXPENSE", "FOOD", "🍔", false)
    val incomeCategory = SyncCategoryDto(UUID.randomUUID().toString(), "Зарплата", "INCOME", "INCOME", "💰", false)
    val log =
        mutableListOf(
            SyncChangeDto(1, "CATEGORY", false, category = expenseCategory),
            SyncChangeDto(2, "CATEGORY", false, category = incomeCategory),
        )
    val received = linkedMapOf<String, CreatedOperationDto>()
    var postCount = 0
    var loseNextResponse = false
    var reject = false
    var pageSize = 200
    var changesCalls = 0
    var failChangeAtCall: Int? = null

    override suspend fun changes(
        authorization: String,
        cursor: String?,
    ): SyncPageDto {
        changesCalls++
        if (changesCalls == failChangeAtCall) throw IOException("Synthetic interruption")
        val parts = cursor?.split(':')
        val after = parts?.get(0)?.toLong() ?: 0
        val upper = parts?.get(1)?.toLong()?.takeIf { it > 0 } ?: log.size.toLong()
        val rows = log.filter { it.revision > after && it.revision <= upper }
        val page = rows.take(pageSize)
        val more = rows.size > page.size
        return SyncPageDto(scope, page, "${if (more) page.last().revision else upper}:${if (more) upper else 0}", more)
    }

    override suspend fun create(
        authorization: String,
        request: CreateOperationDto,
    ): Response<CreatedOperationDto> {
        postCount++
        if (reject) {
            return Response.error(
                400,
                """{"code":"invalid_operation","reason":"category_unavailable"}"""
                    .toResponseBody("application/json".toMediaType()),
            )
        }
        val result =
            received.getOrPut(request.requestId) {
                val operation =
                    SyncOperationDto(
                        UUID.randomUUID().toString(),
                        request.categoryId,
                        request.type,
                        request.amount,
                        "KZT",
                        request.amount,
                        request.occurredOn,
                        "2026-10-18T05:00:00Z",
                        "POSTED",
                        0,
                        (received.size + 1).toLong(),
                        description = request.description,
                        requestId = request.requestId,
                    )
                log += SyncChangeDto((log.size + 1).toLong(), "LEDGER", false, operation = operation)
                CreatedOperationDto(request.requestId, operation)
            }
        if (loseNextResponse) {
            loseNextResponse = false
            throw IOException("Synthetic lost response after commit")
        }
        return Response.success(result)
    }
}
