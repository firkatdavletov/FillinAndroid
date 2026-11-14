package com.firkat.fillin.core.mvi

/**
 * Produces screen state from user actions and interactor events.
 *
 * Both methods must be pure: do not mutate the supplied state or perform side effects.
 * [BaseViewModel] calls them synchronously on Main, before the corresponding handler.
 */
interface Reducer<S : State, A : Action, E : Event> {
    fun reduceAction(
        state: S,
        action: A
    ): S

    fun reduceEvent(
        state: S,
        event: E
    ): S
}
