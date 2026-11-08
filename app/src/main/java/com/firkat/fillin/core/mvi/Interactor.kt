package com.firkat.fillin.core.mvi

import kotlinx.coroutines.flow.Flow

/**
 * Executes business logic for a command and returns its results as a stream of events.
 *
 * The flow may emit multiple events or observe data indefinitely. Each command is collected
 * independently in the ViewModel's scope. Implementations must be safe to call from Main and
 * move blocking or CPU-intensive work to an appropriate dispatcher, either here or downstream.
 * Represent expected business failures as [Event] values and preserve coroutine cancellation.
 * Unexpected exceptions are not intercepted by [BaseViewModel].
 */
fun interface Interactor<C : Command, E : Event> {
    fun execute(command: C): Flow<E>
}
