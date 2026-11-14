package com.firkat.fillin.core.mvi

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {
    private val models = ViewModelStore()

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `construction exposes initial state without invoking handlers or interactor`() = runTest {
        val interactor = RecordingInteractor()
        val initial = CounterState(total = 7, input = "initial")
        val model = model(interactor, initial)

        runCurrent()

        assertEquals(initial, model.state.value)
        assertTrue(model.actionStates.isEmpty())
        assertTrue(model.eventStates.isEmpty())
        assertTrue(interactor.commands.isEmpty())
        assertTrue(model.effects.replayCache.isEmpty())
    }

    @Test
    fun `actions are reduced synchronously before their handler sees state`() = runTest {
        val interactor = RecordingInteractor()
        val model = model(interactor)
        val action = CounterAction.InputChanged("42")

        model.onAction(action)

        assertEquals(CounterState(input = "42"), model.state.value)
        assertEquals(listOf(action to model.state.value), model.actionStates)
        assertTrue(interactor.commands.isEmpty())
    }

    @Test
    fun `action command and multiple events produce state before effects`() = runTest {
        val interactor = RecordingInteractor { command ->
            flow {
                emit(CounterEvent.Added(command.amount))
                emit(CounterEvent.Added(command.amount))
            }
        }
        val model = model(interactor)
        val received = mutableListOf<CounterEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.collect {
                assertTrue(model.state.value.total >= (it as CounterEffect.TotalChanged).total)
                received += it
            }
        }
        val command = CounterCommand(3)

        model.onAction(CounterAction.Run(command))
        assertEquals(1, model.state.value.requests)
        assertEquals(1, model.actionStates.single().second.requests)
        runCurrent()

        assertEquals(listOf(command), interactor.commands)
        assertEquals(CounterState(total = 6, requests = 1), model.state.value)
        assertEquals(listOf(3, 6), model.eventStates.map { it.second.total })
        assertEquals(listOf(CounterEffect.TotalChanged(3), CounterEffect.TotalChanged(6)), received)
        assertTrue(model.commandJobs.single().isCompleted)
    }

    @Test
    fun `parallel commands reduce interleaved events without losing changes`() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        val model = model(
            RecordingInteractor { command ->
                flow {
                    if (command.amount == 1) {
                        emit(CounterEvent.Added(1))
                        releaseFirst.await()
                        emit(CounterEvent.Added(100))
                    } else {
                        emit(CounterEvent.Added(10))
                    }
                }
            }
        )

        model.onAction(CounterAction.Run(CounterCommand(1)))
        runCurrent()
        model.onAction(CounterAction.Run(CounterCommand(10)))
        runCurrent()

        assertTrue(model.commandJobs.first().isActive)
        assertTrue(model.commandJobs.last().isCompleted)
        assertEquals(11, model.state.value.total)

        releaseFirst.complete(Unit)
        runCurrent()

        assertEquals(CounterState(total = 111, requests = 2), model.state.value)
        assertEquals(listOf(1, 11, 111), model.eventStates.map { it.second.total })
    }

    @Test
    fun `long running observation does not block other commands and can be cancelled separately`() = runTest {
        val updates = MutableSharedFlow<CounterEvent>()
        val model = model(
            RecordingInteractor { command ->
                if (command.amount == 0) updates else flowOf(CounterEvent.Added(command.amount))
            }
        )
        model.onAction(CounterAction.Run(CounterCommand(0)))
        runCurrent()
        val observation = model.commandJobs.single()

        updates.emit(CounterEvent.Added(2))
        runCurrent()
        model.onAction(CounterAction.Run(CounterCommand(5)))
        runCurrent()
        assertTrue(observation.isActive)
        assertEquals(7, model.state.value.total)

        observation.cancel()
        runCurrent()
        updates.emit(CounterEvent.Added(100))
        model.onAction(CounterAction.Run(CounterCommand(3)))
        runCurrent()

        assertTrue(observation.isCancelled)
        assertEquals(0, updates.subscriptionCount.value)
        assertEquals(CounterState(total = 10, requests = 3), model.state.value)
    }

    @Test
    fun `clearing ViewModel cancels every command and a suspended effect emission`() = runTest {
        var started = 0
        var cancelled = 0
        val model = model(
            RecordingInteractor {
                flow {
                    started++
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled++
                    }
                }
            }
        )
        val holdSubscriber = CompletableDeferred<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.collect { holdSubscriber.await() }
        }
        model.onAction(CounterAction.Run(CounterCommand(1)))
        model.onAction(CounterAction.Run(CounterCommand(2)))
        model.onAction(CounterAction.Notify("first"))
        runCurrent()
        model.onAction(CounterAction.Notify("second"))
        runCurrent()
        val pendingEffect = model.effectJobs.last()
        assertEquals(2, started)
        assertTrue(pendingEffect.isActive)

        models.clear()
        runCurrent()

        assertEquals(2, cancelled)
        assertTrue(model.commandJobs.all { it.isCancelled })
        assertTrue(pendingEffect.isCancelled)
    }

    @Test
    fun `effects reach all active subscribers without replay after resubscription`() = runTest {
        val model = model()
        val first = mutableListOf<CounterEffect>()
        val second = mutableListOf<CounterEffect>()
        val firstSubscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.collect { first += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.collect { second += it }
        }

        model.onAction(CounterAction.Notify("first"))
        runCurrent()
        firstSubscription.cancel()
        val resubscribed = mutableListOf<CounterEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.collect { resubscribed += it }
        }
        runCurrent()
        assertTrue(resubscribed.isEmpty())

        model.onAction(CounterAction.Notify("second"))
        runCurrent()

        assertEquals(listOf(CounterEffect.Message("first")), first)
        assertEquals(listOf(CounterEffect.Message("first"), CounterEffect.Message("second")), second)
        assertEquals(listOf(CounterEffect.Message("second")), resubscribed)
        assertTrue(model.effects.replayCache.isEmpty())
    }

    @Test
    fun `effects emitted without subscribers are lost`() = runTest {
        val model = model()
        model.onAction(CounterAction.Notify("lost"))
        runCurrent()
        assertTrue(model.effectJobs.single().isCompleted)

        val received = mutableListOf<CounterEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.collect { received += it }
        }
        runCurrent()
        assertTrue(received.isEmpty())

        model.onAction(CounterAction.Notify("visible"))
        runCurrent()

        assertEquals(listOf(CounterEffect.Message("visible")), received)
    }

    @Test
    fun `slow subscriber suspends emission without blocking action and event reduction`() = runTest {
        val model = model()
        val releaseSubscriber = CompletableDeferred<Unit>()
        val received = mutableListOf<CounterEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.collect {
                received += it
                releaseSubscriber.await()
            }
        }
        model.onAction(CounterAction.Notify("first"))
        runCurrent()
        model.onAction(CounterAction.Notify("second"))
        runCurrent()
        val pendingEffect = model.effectJobs.last()
        assertTrue(pendingEffect.isActive)

        model.onAction(CounterAction.InputChanged("still responsive"))
        model.onAction(CounterAction.Run(CounterCommand(4)))
        runCurrent()

        assertEquals(CounterState(total = 4, input = "still responsive", requests = 1), model.state.value)
        assertTrue(pendingEffect.isActive)
        assertEquals(listOf(CounterEffect.Message("first")), received)

        releaseSubscriber.complete(Unit)
        runCurrent()

        assertTrue(pendingEffect.isCompleted)
        assertEquals(
            listOf(CounterEffect.Message("first"), CounterEffect.Message("second"), CounterEffect.TotalChanged(4)),
            received
        )
    }

    @Test
    fun `expected business failure arrives as an event and retry succeeds`() = runTest {
        val model = model(
            RecordingInteractor { command ->
                if (command.amount < 0) {
                    flowOf(CounterEvent.Failed("invalid amount"))
                } else {
                    flowOf(CounterEvent.Added(command.amount))
                }
            }
        )
        val received = mutableListOf<CounterEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.collect { received += it }
        }

        model.onAction(CounterAction.Run(CounterCommand(-1)))
        runCurrent()

        assertEquals("invalid amount", model.state.value.error)
        assertEquals("invalid amount", model.eventStates.single().second.error)
        assertEquals(listOf(CounterEffect.Message("invalid amount")), received)
        assertFalse(model.commandJobs.single().isCancelled)

        model.onAction(CounterAction.Run(CounterCommand(5)))
        runCurrent()

        assertEquals(CounterState(total = 5, requests = 2), model.state.value)
    }

    @Test
    fun `unexpected exceptions from interactor invocation and collection are not swallowed`() {
        val failure = IllegalStateException("unexpected")
        val interactors = listOf(
            Interactor<CounterCommand, CounterEvent> { throw failure },
            Interactor<CounterCommand, CounterEvent> { flow { throw failure } }
        )

        interactors.forEach { interactor ->
            val thrown = assertThrows(IllegalStateException::class.java) {
                runTest {
                    val model = model(interactor)
                    try {
                        model.onAction(CounterAction.Run(CounterCommand(1)))
                        runCurrent()
                        assertTrue(model.commandJobs.single().isCancelled)
                        assertTrue(model.eventStates.isEmpty())
                    } finally {
                        models.clear()
                    }
                }
            }
            assertSame(failure, thrown)
        }
    }

    @Test
    fun `interactor cancellation does not become an event or cancel a sibling command`() = runTest {
        val model = model(
            RecordingInteractor { command ->
                flow {
                    if (command.amount == 0) throw CancellationException("cancelled")
                    emit(CounterEvent.Added(command.amount))
                }
            }
        )

        model.onAction(CounterAction.Run(CounterCommand(0)))
        model.onAction(CounterAction.Run(CounterCommand(8)))
        runCurrent()

        assertTrue(model.commandJobs.first().isCancelled)
        assertFalse(model.commandJobs.last().isCancelled)
        assertEquals(CounterState(total = 8, requests = 2), model.state.value)
        assertEquals(listOf(CounterEvent.Added(8)), model.eventStates.map { it.first })
    }

    @Test
    fun `events are reduced when subclass uses default event handler`() = runTest {
        val model = object : BaseViewModel<CounterState, CounterAction, CounterCommand, CounterEvent, CounterEffect>(
            CounterState(),
            CounterReducer,
            RecordingInteractor()
        ) {
            override fun handleAction(
                action: CounterAction
            ) {
                if (action is CounterAction.Run) execute(action.command)
            }
        }
        models.put("default-handler", model)

        model.onAction(CounterAction.Run(CounterCommand(9)))
        runCurrent()

        assertEquals(CounterState(total = 9, requests = 1), model.state.value)
    }

    private fun model(
        interactor: Interactor<CounterCommand, CounterEvent> = RecordingInteractor(),
        initialState: CounterState = CounterState()
    ) = CounterViewModel(interactor, initialState).also { models.put("counter", it) }

    private data class CounterState(
        val total: Int = 0,
        val input: String = "",
        val requests: Int = 0,
        val error: String? = null
    ) : State

    private sealed interface CounterAction : Action {
        data class InputChanged(val input: String) : CounterAction
        data class Run(val command: CounterCommand) : CounterAction
        data class Notify(val message: String) : CounterAction
    }

    private data class CounterCommand(val amount: Int) : Command

    private sealed interface CounterEvent : Event {
        data class Added(val amount: Int) : CounterEvent
        data class Failed(val message: String) : CounterEvent
    }

    private sealed interface CounterEffect : Effect {
        data class TotalChanged(val total: Int) : CounterEffect
        data class Message(val message: String) : CounterEffect
    }

    private object CounterReducer : Reducer<CounterState, CounterAction, CounterEvent> {
        override fun reduceAction(
            state: CounterState,
            action: CounterAction
        ): CounterState = when (action) {
            is CounterAction.InputChanged -> state.copy(input = action.input)
            is CounterAction.Run -> state.copy(requests = state.requests + 1, error = null)
            is CounterAction.Notify -> state
        }

        override fun reduceEvent(
            state: CounterState,
            event: CounterEvent
        ): CounterState = when (event) {
            is CounterEvent.Added -> state.copy(total = state.total + event.amount)
            is CounterEvent.Failed -> state.copy(error = event.message)
        }
    }

    private class RecordingInteractor(
        private val events: (CounterCommand) -> Flow<CounterEvent> = { flowOf(CounterEvent.Added(it.amount)) }
    ) : Interactor<CounterCommand, CounterEvent> {
        val commands = mutableListOf<CounterCommand>()

        override fun execute(
            command: CounterCommand
        ): Flow<CounterEvent> {
            commands += command
            return events(command)
        }
    }

    private class CounterViewModel(interactor: Interactor<CounterCommand, CounterEvent>, initialState: CounterState) :
        BaseViewModel<CounterState, CounterAction, CounterCommand, CounterEvent, CounterEffect>(
            initialState,
            CounterReducer,
            interactor
        ) {
        val actionStates = mutableListOf<Pair<CounterAction, CounterState>>()
        val eventStates = mutableListOf<Pair<CounterEvent, CounterState>>()
        val commandJobs = mutableListOf<Job>()
        val effectJobs = mutableListOf<Job>()

        override fun handleAction(
            action: CounterAction
        ) {
            actionStates += action to state.value
            when (action) {
                is CounterAction.Run -> commandJobs += execute(action.command)
                is CounterAction.Notify -> effectJobs += emitEffect(CounterEffect.Message(action.message))
                is CounterAction.InputChanged -> Unit
            }
        }

        override fun handleEvent(
            event: CounterEvent
        ) {
            eventStates += event to state.value
            effectJobs += emitEffect(
                when (event) {
                    is CounterEvent.Added -> CounterEffect.TotalChanged(state.value.total)
                    is CounterEvent.Failed -> CounterEffect.Message(event.message)
                }
            )
        }
    }
}
