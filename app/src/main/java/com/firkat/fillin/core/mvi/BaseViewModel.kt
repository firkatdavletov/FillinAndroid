package com.firkat.fillin.core.mvi

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Coordinates the UI's actions, state, business commands and one-time effects.
 *
 * Actions and interactor events are reduced synchronously on Main before their handlers run,
 * so handlers always see the updated [state]. State can only be changed through [reducer].
 * Each [execute] call starts an independent job in [viewModelScope]; subclasses may keep the
 * returned job to cancel or replace an operation. Clearing the ViewModel cancels all jobs.
 *
 * Construction only stores dependencies and the initial state. Start initial commands in the
 * subclass after its fields have been initialized, rather than from an overridden base hook.
 *
 * @param initialState the state immediately available to the UI
 * @param reducer pure transformations for this screen's actions and events
 * @param interactor business logic for this screen's commands
 */
abstract class BaseViewModel<S : State, A : Action, C : Command, E : Event, F : Effect>(
    initialState: S,
    private val reducer: Reducer<S, A, E>,
    private val interactor: Interactor<C, E>
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<F>(replay = 0)

    /**
     * Broadcasts effects to active subscribers without replay or an additional buffer.
     * Effects emitted without subscribers are lost; a new subscriber receives no past effects.
     * A slow subscriber suspends the emission job, without blocking action or event reduction.
     */
    val effects: SharedFlow<F> = mutableEffects.asSharedFlow()

    /** Publishes the reduced state, then handles the action synchronously on Main. */
    @MainThread
    fun onAction(
        action: A
    ) {
        mutableState.value = reducer.reduceAction(mutableState.value, action)
        handleAction(action)
    }

    /** Handles an action after reduction, for example by sending a command or effect. */
    @MainThread
    protected abstract fun handleAction(
        action: A
    )

    /** Handles an interactor event after reduction. Override to send commands or effects. */
    @MainThread
    protected open fun handleEvent(
        event: E
    ) = Unit

    /**
     * Starts an independent command on Main and reduces every event before handling it.
     * Exceptions and cancellation propagate through the job without interception.
     */
    @MainThread
    protected fun execute(
        command: C
    ): Job = viewModelScope.launch {
        interactor.execute(command).collect { event ->
            mutableState.value = reducer.reduceEvent(mutableState.value, event)
            handleEvent(event)
        }
    }

    /** Emits an effect in the ViewModel's scope, suspending until active subscribers receive it. */
    @MainThread
    protected fun emitEffect(
        effect: F
    ): Job = viewModelScope.launch {
        mutableEffects.emit(effect)
    }
}
