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
    single<com.firkat.fillin.feature.budgetsetup.domain.LocalBudgetCreator> { com.firkat.fillin.feature.budgetsetup.data.RoomLocalBudgetCreator(get(), get(), get(), get(), get()) }
    viewModel { com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel(get(), get(), get(), get(), get(), get(), dispatcher = kotlinx.coroutines.Dispatchers.IO,
        access = get(), localCount = (get<com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileRegistry>()
            as com.firkat.fillin.feature.budgetprofile.data.RoomBudgetProfileRegistry).observeLocalCount()) }
    single {
        FirebaseBudgetBackend(BuildConfig.FIREBASE_API_BASE_URL.takeIf(String::isNotBlank)?.let {
            get<Retrofit>().newBuilder().baseUrl(it).client(get<OkHttpClient>(org.koin.core.qualifier.named("cloud")).newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()).build()
        })
    }
    single { FirebaseBudgetRequests(get<FirebaseBudgetBackend>().retrofit?.create(FirebaseBudgetApi::class.java), get(), get()) }
    single<com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitationRepository> {
        com.firkat.fillin.feature.budgetinvitation.data.FirebaseBudgetInvitationRepository(
            get<FirebaseBudgetBackend>().retrofit?.create(com.firkat.fillin.feature.budgetinvitation.data.FirebaseBudgetInvitationApi::class.java), get(), get(), get())
    }
    viewModel { com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationViewModel(get()) }
    single<com.firkat.fillin.feature.budgetsharing.domain.FamilyBudgetRepository> {
        com.firkat.fillin.feature.budgetsharing.data.FirebaseFamilyBudgetRepository(
            get<FirebaseBudgetBackend>().retrofit?.create(com.firkat.fillin.feature.budgetsharing.data.FirebaseFamilyBudgetApi::class.java), get(), get(), get(), get(), get(), get())
    }
    viewModel { com.firkat.fillin.feature.budgetsharing.ui.FamilyBudgetJoinViewModel(get(), get(), dispatcher = kotlinx.coroutines.Dispatchers.IO) }
    viewModel { com.firkat.fillin.feature.budgetsharing.ui.FamilyMembersViewModel(get()) }
    single<BudgetCatalogStore> { RoomBudgetCatalogStore(get()) }
    single<BudgetConnectionRepository> { RoomBudgetConnectionRepository(get(), get(), get(), get(), get(), get(), get(), access = get(), eraseMissing = { uid, retained -> get<com.firkat.fillin.feature.profile.data.AccountCloudEraser>().erase(uid, retained) }) }
    single<BudgetCreationStore> { RoomBudgetCreationStore(get()) }
    single<BudgetCreationRepository> { FirebaseBudgetCreationRepository(get(), get(), get(), access = get()) }
    single<com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadStore> { com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadStore(get()) }
    single<com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadRepository> { com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadRepository(get(), get(), get(), get(), get(), get(), get(), access = get()) }
    viewModel { com.firkat.fillin.feature.budgetupload.ui.LocalBudgetUploadViewModel(get(), get()) }
    viewModel { parameters ->
        BudgetConnectionViewModel(get(), get(), get(), autoStartCreation = parameters.getOrNull<Boolean>() ?: false, dispatcher = kotlinx.coroutines.Dispatchers.IO)
    }
}

private data class FirebaseBudgetBackend(val retrofit: Retrofit?)
