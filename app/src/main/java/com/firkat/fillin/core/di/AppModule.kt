package com.firkat.fillin.core.di

import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.purchasehistory.data.remote.PurchaseHistoryApi
import com.firkat.fillin.feature.purchasehistory.data.repository.PurchaseHistoryRepositoryImpl
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository
import com.firkat.fillin.feature.purchasehistory.domain.usecase.GetPurchaseHistoryPage
import com.firkat.fillin.feature.purchasehistory.ui.PurchaseHistoryViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
    single {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(
                get<Json>().asConverterFactory("application/json".toMediaType()),
            )
            .build()
    }
    single<PurchaseHistoryApi> { get<Retrofit>().create(PurchaseHistoryApi::class.java) }
    single<PurchaseHistoryRepository> {
        PurchaseHistoryRepositoryImpl(
            api = get(),
            apiToken = BuildConfig.MOBILE_API_TOKEN,
        )
    }
    factory { GetPurchaseHistoryPage(repository = get()) }
    viewModel { PurchaseHistoryViewModel(getPurchaseHistoryPage = get()) }
}
