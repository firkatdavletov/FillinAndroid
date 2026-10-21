package com.firkat.fillin.feature.budgetprofile.data

import androidx.work.Data
import com.firkat.fillin.feature.budgetprofile.domain.BudgetSyncIdentity
import java.security.MessageDigest

object BudgetWorkIdentity {
    private const val PROFILE = "budget_profile_id"
    private const val UID = "budget_firebase_uid"
    private const val VERSION = "budget_work_version"

    fun data(identity: BudgetSyncIdentity): Data = Data.Builder()
        .putInt(VERSION, 1).putString(PROFILE, identity.profileId).putString(UID, identity.firebaseUid).build()

    fun read(data: Data): BudgetSyncIdentity? {
        if (data.getInt(VERSION, 0) != 1) return null
        val profile = data.getString(PROFILE)?.takeIf(String::isNotBlank) ?: return null
        return BudgetSyncIdentity(profile, data.getString(UID))
    }

    fun uniqueName(identity: BudgetSyncIdentity): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("${identity.profileId.length}:${identity.profileId}:${identity.firebaseUid ?: ""}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "operation-history-sync-$hash"
    }
}
