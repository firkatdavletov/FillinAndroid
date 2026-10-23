package com.firkat.fillin.feature.budgetconnection.data

import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfile
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.operationhistory.data.sync.SyncBlockedException
import com.firkat.fillin.feature.sync.data.*
import retrofit2.Response
import com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUpload
import kotlinx.serialization.json.Json

/** Every call is permanently bound to the scope's UID and budget ID. */
class FirebaseCloudBudgetApi(private val profile: BudgetProfile, private val requests: FirebaseBudgetRequests, private val upload: suspend () -> LocalBudgetUpload? = { null }, private val json: Json = Json) : CloudBudgetApi {
    init { require(profile.kind == BudgetProfileKind.CLOUD) }
    private val uid = requireNotNull(profile.firebaseUid)
    private val id = requireNotNull(profile.budgetId)

    override suspend fun budget(authorization: String) = requests.budget(uid, id)
    override suspend fun changes(authorization: String, cursor: String?) = requests.changes(uid, id, cursor).budgetBody()
    override suspend fun command(authorization: String, command: BudgetSyncCommand) = requests.command(uid, id, command)
    override suspend fun create(authorization: String, request: CreateCloudBudget): Response<CloudBudget> {
        val saved = upload() ?: throw SyncBlockedException("cloud_creation_not_supported")
        if (saved.firebaseUid != uid || request.householdId != id || json.decodeFromString<CreateCloudBudget>(saved.bootstrapPayload) != request) throw SyncBlockedException("scope_changed")
        val response = requests.importLocal(uid, ImportFirebaseBudgetDto(saved.name, request))
        if (!response.isSuccessful) return Response.error(response.code(), requireNotNull(response.errorBody()))
        val budget = response.budgetBody()
        if (budget.id != id || budget.memberId != request.memberId || budget.role != "OWNER" || budget.name != saved.name) throw SyncBlockedException("invalid_response")
        return requests.budget(uid, id)
    }
}
