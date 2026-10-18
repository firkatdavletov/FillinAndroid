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

@RunWith(AndroidJUnit4::class)
class OfflineDependenciesTest {
    @Test
    fun productionDependencyGraphAndCustomWorkerFactoryResolveWithoutNetworkCalls() {
        val application = ApplicationProvider.getApplicationContext<FillinApplication>()
        val koin = application.getKoin()
        assertTrue(koin.get<OperationHistoryStore>() is OfflineOperationHistoryRepository)
        assertNotNull(koin.get<OperationsSynchronizer>())
        assertNotNull(koin.get<OperationSyncScheduler>())
        assertTrue(application.workManagerConfiguration.workerFactory is OperationWorkerFactory)
        // Initialization only: this test does not enqueue production work.
        assertNotNull(WorkManager.getInstance(application))
    }
}
