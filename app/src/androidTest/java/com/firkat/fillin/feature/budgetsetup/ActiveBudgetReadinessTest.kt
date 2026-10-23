package com.firkat.fillin.feature.budgetsetup

import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetsetup.data.RoomActiveBudgetReadiness
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ActiveBudgetReadinessTest {
    @Test fun followsCommittedReadinessAndReleasesOldAndCancelledBudgetScopes() = runBlocking {
        BudgetConnectionDeviceTest.Environment().use { env ->
            val events = Channel<Boolean>(Channel.UNLIMITED)
            val observer = launch {
                RoomActiveBudgetReadiness(env.manager).observe().collect { events.send(it) }
            }
            try {
                assertFalse(withTimeout(5000) { events.receive() })
                val first = env.manager.active.value!!
                val session = first.scope.get<CloudBudgetSession>()
                session.createLocal("42.00".toBigDecimal(), session.today)
                assertTrue(withTimeout(5000) { events.receive() })

                val second = env.registry.createLocal()
                env.manager.select(second.id)
                assertFalse(withTimeout(5000) { events.receive() })
                assertTrue(first.scope.closed)

                val current = env.manager.active.value!!
                observer.cancelAndJoin()
                env.manager.select(env.registry.createLocal().id)
                assertTrue(current.scope.closed)
            } finally {
                observer.cancelAndJoin()
                events.close()
            }
        }
    }

    @Test fun restoresCurrentAccountsSelectionBeforePublishingReadiness() = runBlocking {
        BudgetConnectionDeviceTest.Environment().use { env ->
            env.auth.login("owner")
            env.manager.restore()
            env.connection.connect("owner", env.connection.budgets("owner").single())
            val cloud = env.manager.active.value!!.profile
            env.auth.logout()
            env.manager.restore()
            assertNotEquals(cloud, env.manager.active.value!!.profile)
            env.auth.login("owner")
            val events = Channel<Boolean>(Channel.UNLIMITED)
            val observer = launch {
                RoomActiveBudgetReadiness(env.manager).observe().collect { events.send(it) }
            }
            try {
                assertTrue(withTimeout(5000) { events.receive() })
                assertEquals(cloud, env.manager.active.value!!.profile)
            } finally {
                observer.cancelAndJoin()
                events.close()
            }
        }
    }
}
