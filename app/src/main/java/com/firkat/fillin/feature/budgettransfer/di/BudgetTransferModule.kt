package com.firkat.fillin.feature.budgettransfer.di

import com.firkat.fillin.feature.budgetprofile.data.RoomBudgetProfileRegistry
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileRegistry
import com.firkat.fillin.feature.budgettransfer.data.BudgetCsvCodec
import com.firkat.fillin.feature.budgettransfer.data.BudgetDocuments
import com.firkat.fillin.feature.budgettransfer.data.BudgetSnapshotValidator
import com.firkat.fillin.feature.budgettransfer.data.RoomBudgetSnapshot
import com.firkat.fillin.feature.budgettransfer.data.RoomBudgetTransfer
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransfer
import com.firkat.fillin.feature.budgettransfer.ui.BudgetTransferViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

internal val budgetTransferModule = module {
    single<BudgetTransfer> {
        val registry = get<BudgetProfileRegistry>() as RoomBudgetProfileRegistry
        val codec = BudgetCsvCodec(BudgetSnapshotValidator(get()))
        RoomBudgetTransfer(
            get(),
            registry,
            get(),
            get(),
            RoomBudgetSnapshot(get()),
            BudgetDocuments(androidContext().contentResolver, codec)
        )
    }
    viewModel { BudgetTransferViewModel(get(), get()) }
}
