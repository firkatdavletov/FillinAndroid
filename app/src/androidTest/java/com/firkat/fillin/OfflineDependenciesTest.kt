package com.firkat.fillin

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.firkat.fillin.feature.operationhistory.data.repository.OfflineOperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationWorkerFactory
import com.firkat.fillin.feature.operationhistory.data.sync.OperationsSynchronizer
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.android.getKoin
import kotlinx.coroutines.runBlocking
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager

@RunWith(AndroidJUnit4::class)
class OfflineDependenciesTest {
    @Test
    fun productionDependencyGraphAndCustomWorkerFactoryResolveWithoutNetworkCalls() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<FillinApplication>()
        val root = application.getKoin()
        val manager = root.get<ActiveBudgetManager>()
        manager.restore()
        val lease = manager.active.value!!.retain()!!
        val scope = lease.scope
        try {
        assertTrue(scope.get<OperationHistoryStore>() is OfflineOperationHistoryRepository)
        assertNotNull(scope.get<OperationsSynchronizer>())
        assertTrue(scope.get<com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository>() is com.firkat.fillin.feature.dashboard.data.repository.OfflineDashboardRepository)
        assertNotNull(scope.get<com.firkat.fillin.feature.sync.domain.BudgetSyncControl>())
        assertTrue(
            scope.get<com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository>() is com.firkat.fillin.feature.categories.data.repository.OfflineCategoriesRepository,
        )
        assertTrue(
            scope.get<com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsRepository>() is com.firkat.fillin.feature.plannedoperations.data.repository.OfflinePlannedOperationsRepository,
        )
        assertNotNull(scope.get<OperationSyncScheduler>())
        assertTrue(application.workManagerConfiguration.workerFactory is OperationWorkerFactory)
        // Initialization only: this test does not enqueue production work.
        assertNotNull(WorkManager.getInstance(application))
        } finally { lease.close() }
    }
}
