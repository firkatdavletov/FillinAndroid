package com.firkat.fillin.feature.budgetprofile

import com.firkat.fillin.feature.budgetprofile.domain.*
import org.junit.Assert.*
import org.junit.Test

class BudgetProfileTest {
    @Test fun localAndLegacyProfilesRemainAvailableWithoutAnAccount() {
        for (kind in listOf(BudgetProfileKind.LOCAL, BudgetProfileKind.LEGACY)) {
            val profile = BudgetProfile("profile", "budget-profile.db", kind)
            assertTrue(profile.accessibleTo(null))
            assertTrue(profile.accessibleTo("another-account"))
        }
    }

    @Test fun cloudProfileRequiresItsExactOwnerUid() {
        val profile =
            BudgetProfile("profile", "budget-profile.db", BudgetProfileKind.CLOUD, "owner-uid", "budget-id", "MEMBER")
        assertTrue(profile.accessibleTo("owner-uid"))
        assertFalse(profile.accessibleTo(null))
        assertFalse(profile.accessibleTo("other-uid"))
    }

    @Test fun archivedMembershipCannotBeSelectedEvenByItsOriginalUid() {
        val profile =
            BudgetProfile("old", "budget-old.db", BudgetProfileKind.CLOUD, "owner", "budget", "MEMBER", archived = true)
        assertFalse(profile.accessibleTo("owner"))
        assertFalse(profile.accessibleTo(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cloudProfileCannotLoseItsOwner() {
        BudgetProfile("profile", "budget-profile.db", BudgetProfileKind.CLOUD)
    }

    @Test(expected = IllegalArgumentException::class)
    fun registryCannotRouteOutsideDatabaseDirectory() {
        BudgetProfile("profile", "../other.db", BudgetProfileKind.LOCAL)
    }
}
