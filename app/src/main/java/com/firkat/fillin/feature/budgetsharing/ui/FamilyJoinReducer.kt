package com.firkat.fillin.feature.budgetsharing.ui

import com.firkat.fillin.core.mvi.Reducer

class FamilyJoinReducer : Reducer<FamilyJoinState, FamilyJoinAction, FamilyJoinEvent> {
    override fun reduceAction(
        state: FamilyJoinState,
        action: FamilyJoinAction
    ): FamilyJoinState = with(state) {
        when (action) {
            is FamilyJoinAction.EditCode -> if (!busy &&
                action.code.length <= 80
            ) {
                copy(code = action.code, error = null, joinedName = null)
            } else {
                this
            }
            FamilyJoinAction.Join -> if (!busy &&
                session.joinUid != null
            ) {
                copy(
                    busy = true,
                    error = null,
                    joinedName = null,
                    operationId =
                    operationId + 1
                )
            } else {
                this
            }
            is FamilyJoinAction.Cancelled -> if (request() == action.request) copy(busy = false) else this
        }
    }
    override fun reduceEvent(
        state: FamilyJoinState,
        event: FamilyJoinEvent
    ): FamilyJoinState = with(state) {
        when (event) {
            is FamilyJoinEvent.SessionChanged -> if (session ==
                event.session
            ) {
                this
            } else {
                FamilyJoinState(
                    event.session,
                    generation = generation + 1,
                    operationId =
                    operationId + 1
                )
            }
            is FamilyJoinEvent.Finished -> if (busy && request() == event.request) {
                copy(
                    busy = false,
                    error = event.error,
                    joinedName = event.name,
                    code = if (event.name != null) "" else code
                )
            } else {
                this
            }
        }
    }
}
