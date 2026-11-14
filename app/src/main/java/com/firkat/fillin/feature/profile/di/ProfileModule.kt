package com.firkat.fillin.feature.profile.di

import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.budgetconnection.data.FirebaseBudgetCreationRepository
import com.firkat.fillin.feature.budgetconnection.data.RoomBudgetConnectionRepository
import com.firkat.fillin.feature.budgetconnection.domain.BudgetConnectionRepository
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationRepository
import com.firkat.fillin.feature.budgetupload.data.RoomLocalBudgetUploadRepository
import com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadRepository
import com.firkat.fillin.feature.profile.data.*
import com.firkat.fillin.feature.profile.domain.DeletionStore
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import com.firkat.fillin.feature.profile.ui.ProfileDeletionViewModel
import com.firkat.fillin.feature.profile.ui.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

val profileModule = module {
    single {
        com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement(androidContext(), get(), get(), get(), get(), get(), get(), get(), access = get(), refreshCatalog = { uid ->
            get<BudgetConnectionRepository>().budgets(uid)
        })
    }
    single<DeletionStore> { EncryptedDeletionStore(androidContext()) }
    single { AccountCloudEraser(androidContext(), get(), get()) }
    single {
        val api = BuildConfig.FIREBASE_API_BASE_URL.takeIf(String::isNotBlank)?.let { url ->
            get<Retrofit>().newBuilder().baseUrl(url)
                .client(
                    get<OkHttpClient>().newBuilder()
                        .callTimeout(20, TimeUnit.SECONDS).build()
                )
                .build().create(ProfileDeletionApi::class.java)
        }
        ProfileDeletionCoordinator(get(), api, get(), token = { uid ->
            val auth = FirebaseAuth.getInstance()
            val user = requireNotNull(auth.currentUser).also { check(it.uid == uid) }
            val token = user.getIdToken(true).await().token
            check(auth.currentUser?.uid == uid)
            requireNotNull(token)
        }, pause = { uid ->
            get<com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement>().pause(uid)
            get<AccountCloudEraser>().pause(uid)
            (get<BudgetCreationRepository>() as FirebaseBudgetCreationRepository).awaitIdle()
            (get<BudgetConnectionRepository>() as RoomBudgetConnectionRepository).awaitIdle()
            (get<LocalBudgetUploadRepository>() as RoomLocalBudgetUploadRepository).awaitIdle()
        }, erase = { uid -> get<AccountCloudEraser>().erase(uid) })
    }
    single<ProfileBudgetsRepository> {
        RoomProfileBudgetsRepository(get(), get(), get(), get(), get(), get(), get(), get(), access = get(), management = get())
    }
    viewModel { ProfileDeletionViewModel(get(), get()) }
    viewModel { com.firkat.fillin.feature.budgetmanagement.ui.BudgetManagementViewModel(get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { com.firkat.fillin.feature.appaccess.ui.AboutAppViewModel(get()) }
}
