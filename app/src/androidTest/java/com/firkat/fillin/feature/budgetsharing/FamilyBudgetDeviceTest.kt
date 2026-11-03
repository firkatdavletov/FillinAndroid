package com.firkat.fillin.feature.budgetsharing

import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
import com.firkat.fillin.feature.budgetsharing.data.*
import com.firkat.fillin.feature.budgetsharing.domain.FamilyInvitationCode
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.sync.*
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.sync.data.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.util.UUID

class FamilyBudgetDeviceTest {
    @Test fun memberKeepsPersonalBudgetAndBothDevicesSeeSharedIncomeExpenseWithoutMixedQueues() = runBlocking {
        val server = Server()
        BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server).use { owner ->
            BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server).use { guest ->
                owner.auth.login("owner"); owner.manager.restore()
                owner.connection.connect("owner", owner.connection.budgets("owner").single())
                guest.auth.login("other"); guest.manager.restore()
                guest.connection.connect("other", guest.connection.budgets("other").single())
                val personal = guest.manager.active.value!!.profile
                val before = guest.database(personal) { it.budget().content()!! }
                val code = server.issue()
                val joined = guest.sharing.join("other", code.lowercase().replace("-", " "))
                assertEquals("MEMBER", joined.role)
                assertEquals(joined.memberId, guest.sharing.join("other", code).memberId)
                assertEquals(2, guest.connection.budgets("other").size)
                val family = guest.manager.active.value!!.profile
                assertNotEquals(personal.databaseName, family.databaseName)
                add(guest, OperationType.Expense, "12.34")
                add(guest, OperationType.Income, "5.00")
                assertEquals(SyncResult.Completed, sync(guest))
                assertEquals(SyncResult.Completed, sync(owner))
                val ownerProfile = owner.manager.active.value!!.profile
                assertEquals(setOf("12.34", "5.00"), owner.database(ownerProfile) { it.budget().content()!!.operations.map { op -> op.amountKzt }.toSet() })
                assertEquals(before, guest.database(personal) { it.budget().content()!! })
                val memberTarget = target(guest)
                assertEquals(listOf("OWNER", "MEMBER"), guest.sharing.members(memberTarget).map { it.role })
                guest.manager.acquire(family.syncIdentity)!!.use { lease ->
                    val failure = runCatching { lease.scope.get<BudgetStore>().enqueue("CREATE_CATEGORY") { it.copy(name = "Forbidden", kind = "EXPENSE", icon = "FOOD") } }.exceptionOrNull()
                    assertEquals("forbidden", (failure as BudgetLocalFailure).code)
                }
                server.offline = true
                val budgets = guest.connection.cachedBudgets("other")
                guest.connection.connect("other", budgets.single { it.id == personal.budgetId })
                add(guest, OperationType.Expense, "1.00")
                val queue = guest.database(personal) { it.budget().content()!!.commands }
                guest.connection.connect("other", budgets.single { it.id == family.budgetId })
                assertEquals(queue, guest.database(personal) { it.budget().content()!!.commands })
                assertTrue(guest.database(family) { it.budget().content()!!.commands.none { it.status == "PENDING" } })
            }
        }
    }

    @Test fun revokedMemberQueueIsArchivedAndANewInvitationCannotResendItsOldCommands() = runBlocking {
        val server = Server()
        var archivedFile: String? = null
        val owner = BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server)
        val guest = BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server)
        try {
            owner.auth.login("owner"); owner.manager.restore()
            owner.connection.connect("owner", owner.connection.budgets("owner").single())
            guest.auth.login("other"); guest.manager.restore()
            val code = server.issue(); guest.sharing.join("other", code)
            val original = guest.manager.active.value!!.profile
            archivedFile = original.databaseName
            add(guest, OperationType.Expense, "7.00")
            val queue = guest.database(original) { it.budget().content()!!.commands }
            val ownerTarget = target(owner)
            val member = owner.sharing.members(ownerTarget).single { it.role == "MEMBER" }
            owner.sharing.revoke(ownerTarget, member)
            assertEquals(SyncResult.Blocked, sync(guest))
            assertEquals(queue, guest.database(original) { it.budget().content()!!.commands })
            assertEquals("invalid_invitation", (runCatching { guest.sharing.join("other", code) }.exceptionOrNull() as BudgetConnectionFailure).code)
            guest.sharing.join("other", server.issue())
            val renewed = guest.manager.active.value!!.profile
            assertNotEquals(original.id, renewed.id)
            assertNotEquals(original.databaseName, renewed.databaseName)
            assertTrue(guest.registry.find(original.id)!!.archived)
            assertFalse(guest.registry.find(original.id)!!.accessibleTo("other"))
            assertFalse(guest.registry.available("other").any { it.id == original.id })
            assertEquals(queue, guest.database(original) { it.budget().content()!!.commands })
            assertTrue(guest.database(renewed) { it.budget().content()!!.commands.isEmpty() })
            add(guest, OperationType.Income, "3.00")
            assertEquals(SyncResult.Completed, sync(guest))
            assertTrue(server.received.none { it.requestId in queue.map { item -> item.requestId } })
            assertEquals(1, server.shared.entries.size)
        } finally {
            guest.close(); owner.close()
            archivedFile?.let { guest.context.deleteDatabase(it) }
        }
    }

    @Test fun failedRenewalDownloadKeepsOriginalProfileAndQueueUntilRetryIsReady() = runBlocking {
        val server = Server()
        val env = BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server)
        var oldFile: String? = null
        try {
            env.auth.login("other"); env.manager.restore()
            env.sharing.join("other", server.issue())
            val original = env.manager.active.value!!.profile
            oldFile = original.databaseName
            add(env, OperationType.Expense, "4.00")
            val queue = env.database(original) { it.budget().content()!!.commands }
            server.revoke("Bearer synthetic-owner", server.shared.scope.householdId, server.guestMember)
            val code = server.issue()
            server.failChanges = true
            assertEquals("server_unavailable", (runCatching { env.sharing.join("other", code) }.exceptionOrNull() as BudgetConnectionFailure).code)
            assertEquals(original, env.manager.active.value!!.profile)
            assertFalse(env.registry.find(original.id)!!.archived)
            assertEquals(queue, env.database(original) { it.budget().content()!!.commands })
            server.failChanges = false
            env.sharing.join("other", code)
            assertNotEquals(original.id, env.manager.active.value!!.profile.id)
            assertTrue(env.registry.find(original.id)!!.archived)
            assertEquals(2, server.newMemberships)
            assertEquals(queue, env.database(original) { it.budget().content()!!.commands })
        } finally { env.close(); oldFile?.let { env.context.deleteDatabase(it) } }
    }

    @Test fun lostJoinResponseSurvivesReopeningAndRetryReturnsOneMembership() = runBlocking {
        val server = Server().apply { loseJoinResponse = true }
        val original = BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server, deleteOnClose = false)
        val registryName = original.registryName; val backend = original.backend
        original.auth.login("other"); original.manager.restore()
        val code = server.issue()
        assertEquals("network", (runCatching { original.sharing.join("other", code) }.exceptionOrNull() as BudgetConnectionFailure).code)
        val member = server.guestMember
        original.close()
        BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server, registryName = registryName, backend = backend).use { reopened ->
            reopened.auth.login("other"); reopened.manager.restore()
            assertEquals(member, reopened.sharing.join("other", code).memberId)
            assertEquals(1, server.newMemberships)
            assertEquals("MEMBER", reopened.manager.active.value!!.profile.role)
        }
    }

    @Test fun accountSwitchAfterServerCommitNeverConnectsTheOldAccountsBudget() = runBlocking {
        val server = Server()
        BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server).use { env ->
            env.auth.login("other"); env.manager.restore()
            val entered = CompletableDeferred<Unit>(); val response = CompletableDeferred<Unit>()
            server.beforeJoinReturn = { entered.complete(Unit); response.await() }
            val joining = async(Dispatchers.IO) { runCatching { env.sharing.join("other", server.issue()) }.exceptionOrNull() }
            withTimeout(5000) { entered.await() }
            env.auth.login("owner"); env.manager.restore()
            response.complete(Unit)
            assertEquals("account_changed", (withTimeout(5000) { joining.await() } as BudgetConnectionFailure).code)
            assertTrue(env.registry.available("owner").none { it.firebaseUid == "other" })
            assertTrue(env.connection.cachedBudgets("owner").isEmpty())
        }
    }

    internal class Server : FirebaseBudgetApi, FirebaseFamilyBudgetApi {
        override suspend fun rename(authorization: String, id: String, request: com.firkat.fillin.feature.budgetconnection.data.RenameBudgetDto): retrofit2.Response<com.firkat.fillin.feature.budgetconnection.data.AvailableBudgetDto> = error("Not used in this fixture")
        override suspend fun delete(authorization: String, id: String, requestId: String): retrofit2.Response<Unit> = error("Not used in this fixture")
        val shared = BudgetConnectionDeviceTest.Server().apply {
            budgetName = "Семейный"
            categories = categories + com.firkat.fillin.feature.operationhistory.data.local.LocalCategory(scope.key, UUID.randomUUID().toString(), "Зарплата", "INCOME", "SALARY", "💰", false, 0, "SALARY")
        }
        val personal = BudgetConnectionDeviceTest.Server().apply { budgetName = "Личный"; openingBalance = "500.00" }
        var guestMember = UUID.randomUUID().toString()
        var guestActive = false
        var newMemberships = 0
        var offline = false
        var failChanges = false
        var loseJoinResponse = false
        var beforeJoinReturn: (suspend () -> Unit)? = null
        private var issued = 0
        private var currentCode: String? = null
        private val accepted = linkedMapOf<String, String>()
        val received = mutableListOf<BudgetSyncCommand>()
        fun issue(): String = (++issued).toString().padStart(20, '0').chunked(5).joinToString("-").also { currentCode = it }
        private fun uid(token: String) = token.removePrefix("Bearer synthetic-")
        private fun network() { if (offline) throw IOException("Synthetic offline") }
        private fun member(token: String, id: String): Pair<String, String>? = when {
            id == shared.scope.householdId && uid(token) == "owner" -> shared.scope.memberId to "OWNER"
            id == shared.scope.householdId && uid(token) == "other" && guestActive -> guestMember to "MEMBER"
            id == personal.scope.householdId && uid(token) == "other" -> personal.scope.memberId to "OWNER"
            else -> null
        }
        private fun source(id: String) = if (id == shared.scope.householdId) shared else personal
        private fun summary(token: String, id: String): AvailableBudgetDto {
            val (memberId, role) = member(token, id)!!
            return AvailableBudgetDto(id, source(id).budgetName, memberId, role)
        }
        override suspend fun budgets(authorization: String): Response<List<AvailableBudgetDto>> {
            network(); return Response.success(listOf(shared.scope.householdId, personal.scope.householdId).filter { member(authorization, it) != null }.map { summary(authorization, it) })
        }
        override suspend fun budget(authorization: String, budgetId: String): Response<CloudBudget> {
            network(); val actor = member(authorization, budgetId) ?: return denied()
            return Response.success(CloudBudget(source(budgetId).scope.copy(memberId = actor.first, role = actor.second)))
        }
        override suspend fun changes(authorization: String, budgetId: String, cursor: String?): Response<SyncPageDto> {
            network(); val actor = member(authorization, budgetId) ?: return denied()
            if (failChanges) return Response.error(503, "{}".toResponseBody())
            val page = source(budgetId).changes("Bearer synthetic-owner", budgetId, cursor).body()!!
            return Response.success(page.copy(scope = page.scope.copy(memberId = actor.first, role = actor.second)))
        }
        override suspend fun command(authorization: String, budgetId: String, command: BudgetSyncCommand): Response<BudgetCommandReceipt> {
            network(); val actor = member(authorization, budgetId) ?: return denied()
            if (command.memberId != actor.first) return Response.error(409, "{\"code\":\"scope_changed\"}".toResponseBody())
            received += command
            return source(budgetId).command("Bearer synthetic-owner", budgetId, command.copy(memberId = source(budgetId).scope.memberId))
        }
        override suspend fun join(authorization: String, request: AcceptInvitationDto): Response<AvailableBudgetDto> {
            network()
            val code = FamilyInvitationCode.normalize(request.code)
            if (uid(authorization) != "other") return denied()
            val previous = accepted[code]
            if (previous != null) {
                if (!guestActive || previous != guestMember) return invalid()
            } else {
                if (code != currentCode) return invalid()
                if (!guestActive) { guestMember = UUID.randomUUID().toString(); guestActive = true; newMemberships++ }
                accepted[code] = guestMember; currentCode = null
            }
            beforeJoinReturn?.invoke()
            if (loseJoinResponse) { loseJoinResponse = false; throw IOException("Synthetic lost join response") }
            return Response.success(summary(authorization, shared.scope.householdId))
        }
        override suspend fun members(authorization: String, budgetId: String): Response<FamilyMembersDto> {
            network(); member(authorization, budgetId) ?: return denied()
            val ownerUid = if (budgetId == shared.scope.householdId) "owner" else "other"
            val rows = mutableListOf(FamilyMemberDto(source(budgetId).scope.memberId, "Owner", "OWNER", "2026-09-01T00:00:00Z", uid(authorization) == ownerUid))
            if (budgetId == shared.scope.householdId && guestActive) rows += FamilyMemberDto(guestMember, "Guest", "MEMBER", Instant.now().toString(), uid(authorization) == "other")
            return Response.success(FamilyMembersDto(budgetId, rows))
        }
        override suspend fun revoke(authorization: String, budgetId: String, memberId: String): Response<Unit> {
            network(); if (member(authorization, budgetId)?.second != "OWNER") return denied()
            if (budgetId == shared.scope.householdId && memberId == guestMember) guestActive = false
            return Response.success<Unit>(204, null)
        }
        override suspend fun create(authorization: String, request: CreateFirebaseBudgetDto): Response<AvailableBudgetDto> = error("Unused")
        override suspend fun importLocal(authorization: String, request: ImportFirebaseBudgetDto): Response<AvailableBudgetDto> = error("Unused")
        private fun <T> denied(): Response<T> = Response.error(403, "{\"code\":\"access_denied\"}".toResponseBody())
        private fun <T> invalid(): Response<T> = Response.error(409, "{\"code\":\"invalid_invitation\"}".toResponseBody())
    }
    companion object {
        internal suspend fun target(env: BudgetConnectionDeviceTest.Environment): InvitationTarget = withTimeout(5000) { env.sharing.target.first { it != null }!! }
        internal suspend fun add(env: BudgetConnectionDeviceTest.Environment, type: OperationType, amount: String) {
            env.manager.acquire(env.manager.active.value!!.profile.syncIdentity)!!.use { lease ->
                val content = lease.scope.get<OperationDatabase>().budget().content()!!
                val category = content.categories.first { it.kind == if (type == OperationType.Expense) "EXPENSE" else "INCOME" }
                lease.scope.get<OperationHistoryStore>().create(NewOperation(type, category.id, amount.toBigDecimal(), lease.scope.get<CloudBudgetSession>().today, null), null)
            }
        }
        internal suspend fun sync(env: BudgetConnectionDeviceTest.Environment) = env.manager.acquire(env.manager.active.value!!.profile.syncIdentity)!!.use { it.scope.get<OperationsSynchronizer>().synchronize() }
    }
}
