package com.firkat.fillin.feature.bottomsheets.di

import com.firkat.fillin.feature.bottomsheets.presentation.TestBottomSheetViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bottomSheetsModule = module {
    viewModel { parameters -> TestBottomSheetViewModel(parameters.get(), parameters.get()) }
}
