package com.firkat.fillin.feature.launch

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.feature.budgetprofile.BudgetProfilesDeviceTest
import com.firkat.fillin.feature.launch.data.RoomLocalBudgetRestorer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLocalBudgetRestorerTest {
    @Test fun anAutomaticallyRegisteredEmptyProfileIsNotACreatedBudget() = runBlocking {
        BudgetProfilesDeviceTest.Environment().use { env ->
            env.manager.restore()
            assertFalse(RoomLocalBudgetRestorer(env.registry, env.factory, env.manager).restoreCreatedBudget())
        }
    }

    @Test fun restoresTheSelectedLocalBudgetWithItsOriginalData() = runBlocking {
        BudgetProfilesDeviceTest.Environment().use { env ->
            env.manager.restore()
            val selected = env.manager.active.value!!.profile
            val snapshot = env.seed(selected, "175.00", "12.50")
            assertTrue(RoomLocalBudgetRestorer(env.registry, env.factory, env.manager).restoreCreatedBudget())
            assertEquals(selected, env.manager.active.value!!.profile)
            assertEquals(snapshot, env.snapshot(selected))
        }
    }

    @Test fun findsACreatedBudgetWhenTheSelectedLocalProfileIsEmpty() = runBlocking {
        BudgetProfilesDeviceTest.Environment().use { env ->
            env.manager.restore()
            val empty = env.manager.active.value!!.profile
            val created = env.registry.createLocal()
            val snapshot = env.seed(created, "600.00", "15.00")
            env.manager.select(empty.id)
            assertTrue(RoomLocalBudgetRestorer(env.registry, env.factory, env.manager).restoreCreatedBudget())
            assertEquals(created, env.manager.active.value!!.profile)
            assertEquals(created, env.registry.selected(null))
            assertEquals(snapshot, env.snapshot(created))
        }
    }

    @Test fun aCachedCloudBudgetDoesNotLetAnAnonymousUserSkipAuth() = runBlocking {
        BudgetProfilesDeviceTest.Environment().use { env ->
            env.auth.login("account-a")
            env.manager.restore()
            val cloud = env.registry.registerCloud("account-a", "cloud", "OWNER")
            env.seed(cloud, "500.00", "25.00")
            env.auth.signOut()
            assertFalse(RoomLocalBudgetRestorer(env.registry, env.factory, env.manager).restoreCreatedBudget())
        }
    }
}
