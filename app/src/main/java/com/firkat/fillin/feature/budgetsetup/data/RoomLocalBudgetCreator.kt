package com.firkat.fillin.feature.budgetsetup.data

import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileRegistry
import com.firkat.fillin.feature.budgetsetup.domain.LocalBudgetCreator
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.feature.sync.data.LocalOnlyCloudApi
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class RoomLocalBudgetCreator(
    private val auth: AuthRepository,
    private val profiles: BudgetProfileRegistry,
    private val active: ActiveBudgetManager,
    private val databases: BudgetDatabaseFactory,
    private val json: Json,
) : LocalBudgetCreator {
    override suspend fun createLocal(name: String?, balance: BigDecimal, startedOn: LocalDate) {
        require(balance.scale() <= 2 && balance.abs() <= BigDecimal("99999999999999999.99"))
        require(startedOn.year >= 1 && startedOn <= LocalDate.now(ZoneId.of("Asia/Almaty")))
        val title = name?.trim()
        require(title == null || title.length in 1..80 && title.none(Char::isISOControl))
        val uid = currentUid()
        val profile = if (title == null) profiles.createLocal() else profiles.createLocal(title)
        val database = databases.open(profile)
        try {
            CloudBudgetSession(database, LocalOnlyCloudApi, "", json, OperationSyncScheduler {})
                .createLocal(balance, startedOn)
        } finally { database.close() }
        currentCoroutineContext().ensureActive()
        if (uid != currentUid()) throw BudgetConnectionFailure("account_changed")
        active.select(profile.id, expectedUid = uid, requireExpectedAccount = true)
    }
    private fun currentUid() = (auth.session.value as? AuthSession.Authenticated)?.user?.uid
}
