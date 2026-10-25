package com.firkat.fillin.feature.sync.data

import com.firkat.fillin.feature.operationhistory.data.remote.*
import com.firkat.fillin.feature.operationhistory.data.sync.SyncBlockedException
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import java.math.BigDecimal

/** Local/legacy profiles have no HTTP transport. Connecting explicitly creates a Firebase scope. */
object LocalOnlyCloudApi : CloudBudgetApi {
    override suspend fun budget(authorization: String): Nothing = unavailable()
    override suspend fun create(authorization: String, request: CreateCloudBudget): Nothing = unavailable()
    override suspend fun changes(authorization: String, cursor: String?): Nothing = unavailable()
    override suspend fun command(authorization: String, command: BudgetSyncCommand): Nothing = unavailable()
}

object RetiredOperationSyncApi : OperationSyncApi {
    override suspend fun changes(authorization: String, cursor: String?): Nothing = unavailable()
    override suspend fun create(authorization: String, request: CreateOperationDto): Nothing = unavailable()
}

object RetiredBudgetSyncApi : BudgetSyncApi {
    override suspend fun command(authorization: String, command: BudgetSyncCommand): Nothing = unavailable()
}

object RetiredOperationHistory : OperationHistoryRepository {
    override suspend fun getOperations(limit: Int, cursor: Long?, filter: OperationHistoryFilter): Nothing = denied()
    override suspend fun updateOperationAmount(id: String, version: Long, amount: BigDecimal): Nothing = denied()
    override suspend fun refundPurchase(id: String, version: Long): Nothing = denied()
    private fun denied(): Nothing = throw OperationHistoryException.Unauthorized
}

private fun unavailable(): Nothing = throw SyncBlockedException("sign_in_required")
