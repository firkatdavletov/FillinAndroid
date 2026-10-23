package com.firkat.fillin.feature.budgetconnection.di

import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

val budgetConnectionModule = module {
    single {
        val api = BuildConfig.FIREBASE_API_BASE_URL.takeIf(String::isNotBlank)?.let {
            get<Retrofit>().newBuilder().baseUrl(it).client(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build())
                .build().create(FirebaseBudgetApi::class.java)
        }
        FirebaseBudgetRequests(api, get())
    }
    single<BudgetCatalogStore> { RoomBudgetCatalogStore(get()) }
    single<BudgetConnectionRepository> { RoomBudgetConnectionRepository(get(), get(), get(), get(), get(), get(), get()) }
    single<BudgetCreationStore> { RoomBudgetCreationStore(get()) }
    single<BudgetCreationRepository> { FirebaseBudgetCreationRepository(get(), get(), get()) }
    single<com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadStore> { com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadStore(get()) }
    single<com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadRepository> { com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadRepository(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { com.firkat.fillin.feature.budgetupload.ui.LocalBudgetUploadViewModel(get(), get()) }
    viewModel { parameters ->
        BudgetConnectionViewModel(get(), get(), get(), autoStartCreation = parameters.getOrNull<Boolean>() ?: false)
    }
}
