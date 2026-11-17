package com.firkat.fillin.feature.budgettransfer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.budgetprofile.domain.BudgetSyncIdentity
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransfer
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransferException
import com.firkat.fillin.feature.budgettransfer.domain.TransferFailure
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class BudgetTransferState(
    val canExport: Boolean = false,
    val busy: Boolean = false,
    val working: Boolean = false,
    val result: BudgetTransferResult? = null
)

internal sealed interface BudgetTransferResult {
    data object Exported : BudgetTransferResult
    data class Imported(val selected: Boolean) : BudgetTransferResult
    data class Failed(val reason: TransferFailure?, val row: Int? = null) : BudgetTransferResult
}

internal class BudgetTransferViewModel(private val transfer: BudgetTransfer, private val savedState: SavedStateHandle) :
    ViewModel() {
    private val source = transfer.exportSource.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val picker = savedState.getStateFlow<String?>(PICKER, null)
    private val working = MutableStateFlow(false)
    private val result = MutableStateFlow<BudgetTransferResult?>(null)
    val state = combine(source, picker, working, result) { current, pending, active, outcome ->
        BudgetTransferState(current != null, pending != null || active, active, outcome)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BudgetTransferState())

    fun beginExport(): String? {
        val current = source.value ?: return null
        if (!begin(EXPORT)) return null
        savedState[SOURCE_ID] = current.profileId
        savedState[SOURCE_UID] = current.firebaseUid
        return "saldomi-budget-${LocalDateTime.now().format(FILE_DATE)}.csv"
    }

    fun beginImport(): Boolean = begin(IMPORT)

    fun exportFile(
        document: String?
    ) {
        if (picker.value != EXPORT) return
        val id = savedState.get<String>(SOURCE_ID)
        val uid = savedState.get<String>(SOURCE_UID)
        clearPicker()
        if (document == null) return
        if (id == null) {
            result.value = BudgetTransferResult.Failed(TransferFailure.SourceUnavailable)
            return
        }
        runTransfer {
            transfer.export(BudgetSyncIdentity(id, uid), document)
            BudgetTransferResult.Exported
        }
    }

    fun importFile(
        document: String?,
        fallbackName: String,
        nameSuffix: String
    ) {
        if (picker.value != IMPORT) return
        clearPicker()
        if (document != null) {
            runTransfer { BudgetTransferResult.Imported(transfer.import(document, fallbackName, nameSuffix)) }
        }
    }

    fun pickerUnavailable() {
        clearPicker()
        result.value = BudgetTransferResult.Failed(TransferFailure.FileAccess)
    }

    private fun begin(
        kind: String
    ): Boolean {
        if (picker.value != null || working.value) return false
        result.value = null
        savedState[PICKER] = kind
        return true
    }

    private fun clearPicker() {
        savedState.set<String?>(PICKER, null)
        savedState.remove<String>(SOURCE_ID)
        savedState.remove<String>(SOURCE_UID)
    }

    private fun runTransfer(
        block: suspend () -> BudgetTransferResult
    ) {
        working.value = true
        viewModelScope.launch {
            try {
                result.value = block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: BudgetTransferException) {
                result.value = BudgetTransferResult.Failed(failure.reason, failure.row)
            } catch (_: Exception) {
                result.value = BudgetTransferResult.Failed(null)
            } finally {
                working.value = false
            }
        }
    }

    private companion object {
        const val PICKER = "budget_csv_picker"
        const val SOURCE_ID = "budget_csv_source_id"
        const val SOURCE_UID = "budget_csv_source_uid"
        const val EXPORT = "export"
        const val IMPORT = "import"
        val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmmss", Locale.ROOT)
    }
}
