package com.firkat.fillin.feature.profile.di

import com.firkat.fillin.feature.profile.data.RoomProfileBudgetsRepository
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import com.firkat.fillin.feature.profile.ui.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {
    single<ProfileBudgetsRepository> { RoomProfileBudgetsRepository(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
}
