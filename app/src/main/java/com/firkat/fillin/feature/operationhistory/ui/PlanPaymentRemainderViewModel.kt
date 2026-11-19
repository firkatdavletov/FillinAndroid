package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanPaymentRemainder
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanPaymentRemainderStore
import com.firkat.fillin.feature.sync.data.BudgetLocalFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class PaymentRemainderMessage { Changed, SaveFailed }

internal data class PlanPaymentRemainderState(
    val remainder: PlanPaymentRemainder? = null,
    val saving: Boolean = false,
    val closed: Boolean = false,
    val message: PaymentRemainderMessage? = null
)

internal class PlanPaymentRemainderViewModel(
    private val operationId: String,
    targetKey: String,
    private val store: PlanPaymentRemainderStore
) : ViewModel() {
    private val mutable = MutableStateFlow(PlanPaymentRemainderState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            store.observeLinks(operationId).catch {
                mutable.update { it.copy(closed = true) }
            }.collect { links ->
                val remainder = links.remainder?.takeIf { it.plan.target.stableKey == targetKey }
                mutable.update {
                    val changed = it.remainder != null && remainder != null && it.remainder != remainder && !it.saving
                    it.copy(
                        remainder = remainder,
                        closed = it.closed || remainder == null,
                        message = if (changed) PaymentRemainderMessage.Changed else it.message
                    )
                }
            }
        }
    }

    fun canDismiss(): Boolean = !state.value.saving

    fun decide(shown: PlanPaymentRemainder, close: Boolean) {
        val current = state.value
        if (current.saving || current.closed) return
        if (current.remainder != shown) {
            mutable.update { it.copy(message = PaymentRemainderMessage.Changed) }
            return
        }
        mutable.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                store.decide(operationId, shown, close)
                mutable.update { it.copy(closed = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when ((error as? BudgetLocalFailure)?.code) {
                    "plan_changed", "plan_unavailable" -> PaymentRemainderMessage.Changed
                    else -> PaymentRemainderMessage.SaveFailed
                }
                mutable.update { it.copy(message = message) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }
}
