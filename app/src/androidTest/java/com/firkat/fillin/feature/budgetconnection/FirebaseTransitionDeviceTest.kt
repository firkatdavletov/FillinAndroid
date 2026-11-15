package com.firkat.fillin.feature.budgetconnection

import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.budgetsharing.FamilyBudgetDeviceTest
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.sync.SyncResult
import com.firkat.fillin.feature.sync.data.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

/** Full data lifecycle with isolated stores and synthetic Firebase identities; no real backend writes. */
class FirebaseTransitionDeviceTest {
    @Test fun localUploadSecondBudgetFamilyMembershipLogoutAndRecoveryKeepMoneyAndQueuesSeparate() = runBlocking {
        val family = FamilyBudgetDeviceTest.Server()
        var extra: BudgetConnectionDeviceTest.Server? = null
        var imported: ImportFirebaseBudgetDto? = null
        val api = object : FirebaseBudgetApi by family {
            override suspend fun importLocal(
                authorization: String,
                request: ImportFirebaseBudgetDto
            ): Response<AvailableBudgetDto> {
                check(authorization == "Bearer synthetic-owner")
                if (imported == null) {
                    imported = request
                    family.shared.apply {
                        budgetName = request.name
                        val source = request.budget
                        scope = scope.copy(
                            householdId = source.householdId,
                            memberId = source.memberId,
                            accountingStartedAt = LocalDate.parse(
                                source.accountingStartedOn
                            ).atStartOfDay(ZoneId.of("Asia/Almaty")).toInstant().toString()
                        )
                        openingBalance = source.openingBalance
                        categories = source.categories.map {
                            LocalCategory(
                                scope.key, it.id, it.name, it.kind, it.icon,
                                if (it.kind == "INCOME") "💰" else "🍔", false, 0, it.dictionaryKey
                            )
                        }
                    }
                } else {
                    check(imported == request)
                }
                val scope = family.shared.scope
                return Response.success(AvailableBudgetDto(scope.householdId, request.name, scope.memberId, "OWNER"))
            }
            override suspend fun create(
                authorization: String,
                request: CreateFirebaseBudgetDto
            ): Response<AvailableBudgetDto> {
                val server =
                    extra ?: BudgetConnectionDeviceTest.Server().apply { budgetExists = false }.also { extra = it }
                return server.create(authorization, request)
            }
            override suspend fun budgets(
                authorization: String
            ): Response<List<AvailableBudgetDto>> {
                val items = family.budgets(authorization).body()!!
                val additional = extra?.takeIf { authorization == "Bearer synthetic-owner" }?.let {
                    listOf(AvailableBudgetDto(it.scope.householdId, it.budgetName, it.scope.memberId, "OWNER"))
                }.orEmpty()
                return Response.success(items + additional)
            }
            override suspend fun budget(
                authorization: String,
                budgetId: String
            ) = extra?.takeIf { it.scope.householdId == budgetId }?.budget(authorization, budgetId)
                ?: family.budget(authorization, budgetId)
            override suspend fun changes(
                authorization: String,
                budgetId: String,
                cursor: String?
            ) = extra?.takeIf { it.scope.householdId == budgetId }?.changes(authorization, budgetId, cursor)
                ?: family.changes(authorization, budgetId, cursor)
            override suspend fun command(
                authorization: String,
                budgetId: String,
                command: BudgetSyncCommand
            ) = extra?.takeIf { it.scope.householdId == budgetId }?.command(authorization, budgetId, command)
                ?: family.command(authorization, budgetId, command)
        }
        BudgetConnectionDeviceTest.Environment(apiOverride = api, familyApi = family).use { owner ->
            owner.manager.restore()
            val local = owner.manager.active.value!!.profile
            local.let {
                owner.manager.active.value!!.scope.get<CloudBudgetSession>().run {
                    createLocal("100.00".toBigDecimal(), today.minusDays(1))
                }
            }
            BudgetRecoveryDeviceTest.expense(owner, "12.34")
            val original = owner.database(local) { it.budget().content()!! }
            owner.auth.login("owner")
            owner.manager.restore()
            owner.uploadConnection.connect("owner", local.id, "С устройства")
            val shared = owner.manager.active.value!!.profile
            assertEquals(BudgetProfileKind.CLOUD, shared.kind)
            assertEquals(local.id, shared.id)
            assertEquals(original.commands, owner.database(shared) { it.budget().content()!!.commands })
            assertEquals(SyncResult.Completed, BudgetRecoveryDeviceTest.sync(owner))
            assertEquals(original.sync.householdId, family.shared.scope.householdId)
            assertEquals(original.commands.single().requestId, family.shared.receipts.keys.single())
            owner.creation.create("owner", BudgetRecoveryDeviceTest.draft("Второй", "200.00"))
            val second = owner.manager.active.value!!.profile
            assertNotEquals(shared.databaseName, second.databaseName)
            BudgetRecoveryDeviceTest.expense(owner, "5.00")
            val pending = owner.database(second) { it.budget().content()!!.commands }
            owner.manager.select(shared.id)
            BudgetConnectionDeviceTest.Environment(apiOverride = api, familyApi = family).use { guest ->
                guest.auth.login("other")
                guest.manager.restore()
                guest.sharing.join("other", family.issue())
                assertEquals("MEMBER", guest.manager.active.value!!.profile.role)
                assertEquals(2, guest.connection.budgets("other").size)
                BudgetRecoveryDeviceTest.expense(guest, "4.00")
                assertEquals(SyncResult.Completed, BudgetRecoveryDeviceTest.sync(guest))
                assertEquals(SyncResult.Completed, BudgetRecoveryDeviceTest.sync(owner))
                assertEquals("16.34", family.shared.entries.sumOf { it.amountKzt.toBigDecimal() }.toPlainString())
                assertTrue(extra!!.entries.isEmpty())
            }
            owner.auth.signOut()
            owner.manager.restore()
            assertTrue(owner.registry.available(null).none { it.kind == BudgetProfileKind.CLOUD })
            owner.auth.login("other")
            owner.manager.restore()
            assertTrue(owner.registry.available("other").none { it.id == shared.id || it.id == second.id })
            owner.auth.login("owner")
            owner.manager.restore()
            assertEquals(shared, owner.manager.active.value!!.profile)
            assertEquals(pending, owner.database(second) { it.budget().content()!!.commands })
            owner.manager.select(second.id)
            assertEquals(SyncResult.Completed, BudgetRecoveryDeviceTest.sync(owner))
            assertEquals("5.00", extra!!.entries.single().amountKzt)
            assertEquals(1, extra!!.receipts.size)
            assertEquals(2, family.shared.entries.size)
        }
    }
}
