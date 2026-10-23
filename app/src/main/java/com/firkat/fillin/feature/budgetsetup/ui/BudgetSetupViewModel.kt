package com.firkat.fillin.feature.budgetsetup.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository
import com.firkat.fillin.feature.budgetsetup.domain.CreateLocalBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BudgetSetupViewModel(
    session: BudgetSetupRepository,
    private val createLocal: CreateLocalBudget = CreateLocalBudget(session),
) : ViewModel() {
    val defaultStartDate = session.today
    val ready = session.ready.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun create(balance: String, date: String) {
        if (busy || ready.value == true) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                createLocal(balance.trim().replace(',', '.').toBigDecimal(), LocalDate.parse(date))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = setupError(failure)
            } finally {
                busy = false
            }
        }
    }
}
