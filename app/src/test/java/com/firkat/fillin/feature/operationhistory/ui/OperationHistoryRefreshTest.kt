package com.firkat.fillin.feature.operationhistory.ui

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationHistoryRefreshTest {
    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `refresh schedules sync and keeps visible history and local page window on failure`() = runTest {
        val repository = FakeOfflineHistory()
        repository.operations.value = (1..45).map { FakeOfflineHistory.purchase("entry-$it") }
        val model = model(repository)
        runCurrent()
        assertEquals(20, model.uiState.value.operations.size)
        model.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(40, model.uiState.value.operations.size)
        model.onEvent(OperationHistoryEvent.Refresh)
        runCurrent()
        repository.context.value = repository.context.value.copy(isSyncing = true)
        runCurrent()
        assertTrue(model.uiState.value.isRefreshing)
        assertEquals(40, model.uiState.value.operations.size)
        repository.context.value = repository.context.value.copy(isSyncing = false, syncError = "network")
        runCurrent()
        assertFalse(model.uiState.value.isRefreshing)
        assertEquals(40, model.uiState.value.operations.size)
        assertEquals(40, repository.subscriptions.last().second)
        model.onEvent(OperationHistoryEvent.Retry)
        assertEquals(2, repository.syncRequests)
    }

    @Test fun `incomplete history does not expose final totals`() = runTest {
        val repository = FakeOfflineHistory()
        repository.context.value = repository.context.value.copy(initialSyncComplete = false)
        val model = model(repository)
        runCurrent()
        assertNull(model.uiState.value.periodTotals)
    }

    private fun model(
        repository: FakeOfflineHistory
    ) = OperationHistoryViewModel(
        repository,
        Clock.fixed(Instant.parse("2026-10-18T05:00:00Z"), ZoneId.of("Asia/Almaty"))
    )
}
