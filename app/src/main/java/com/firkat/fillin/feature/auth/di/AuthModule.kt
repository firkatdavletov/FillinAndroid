package com.firkat.fillin.feature.auth.di

import androidx.credentials.CredentialManager
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.auth.data.FirebaseAuthApi
import com.firkat.fillin.feature.auth.data.FirebaseAuthGateway
import com.firkat.fillin.feature.auth.data.FirebaseAuthRepository
import com.firkat.fillin.feature.auth.data.FirebaseServerAuthRepository
import com.firkat.fillin.feature.auth.data.SdkFirebaseAuthGateway
import com.firkat.fillin.feature.auth.data.SdkFirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.ServerAuthRepository
import com.firkat.fillin.feature.auth.ui.CredentialManagerGoogleSignInLauncher
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.auth.ui.interactor.AuthInteractor
import com.firkat.fillin.feature.auth.ui.interactor.AuthReducer
import com.firkat.fillin.feature.auth.ui.interactor.AuthState
import com.firkat.fillin.feature.auth.ui.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import retrofit2.Retrofit

val authModule = module {
    single { CredentialManager.create(androidContext()) }
    single<FirebaseAuthGateway> { SdkFirebaseAuthGateway(FirebaseAuth.getInstance()) }
    single {
        FirebaseAuthRepository(
            if (BuildConfig.FIREBASE_CONFIGURED) get<FirebaseAuthGateway>() else null,
            get<CredentialManager>(),
            get<com.firkat.fillin.feature.appaccess.domain.AppAccess>()
        )
    } onClose { it?.close() }
    single<AuthRepository> { get<FirebaseAuthRepository>() }
    single<FirebaseIdTokenProvider> {
        SdkFirebaseIdTokenProvider(if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null, get(), { uid ->
            get<com.firkat.fillin.feature.profile.domain.DeletionStore>().blocked(uid)
        }, { uid ->
            get<com.firkat.fillin.feature.profile.domain.DeletionStore>().block(uid)
            if (FirebaseAuth.getInstance().currentUser?.uid == uid) get<AuthRepository>().signOut()
        }, access = get())
    }
    single<ServerAuthRepository> {
        val api = BuildConfig.FIREBASE_API_BASE_URL.takeIf(String::isNotBlank)?.let { url ->
            get<Retrofit>().newBuilder().baseUrl(url)
                .client(
                    get<OkHttpClient>(org.koin.core.qualifier.named("cloud")).newBuilder()
                        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
                        .callTimeout(20, TimeUnit.SECONDS).build()
                )
                .build().create(FirebaseAuthApi::class.java)
        }
        FirebaseServerAuthRepository(api, get(), get())
    }
    single<GoogleSignInLauncher> { CredentialManagerGoogleSignInLauncher(get()) }
    single<CoroutineDispatcher>(named("authIo")) { Dispatchers.IO }
    factory { AuthReducer() }
    factory { AuthInteractor(get(), get(), get(named("authIo")), access = get()) }
    viewModel {
        AuthViewModel(
            AuthState(session = get<AuthRepository>().session.value),
            get<AuthReducer>(),
            get<AuthInteractor>()
        )
    }
}
