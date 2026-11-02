package com.firkat.fillin.feature.auth.di

import androidx.credentials.CredentialManager
import com.firkat.fillin.feature.auth.data.FirebaseAuthApi
import com.firkat.fillin.feature.auth.data.FirebaseServerAuthRepository
import com.firkat.fillin.feature.auth.data.SdkFirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.ServerAuthRepository
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.auth.data.FirebaseAuthRepository
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.AuthState
import com.firkat.fillin.feature.auth.ui.AuthReducer
import com.firkat.fillin.feature.auth.ui.AuthInteractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import com.firkat.fillin.feature.auth.ui.CredentialManagerGoogleSignInLauncher
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.google.firebase.auth.FirebaseAuth
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

val authModule = module {
    single { CredentialManager.create(androidContext()) }
    single {
        FirebaseAuthRepository(
            if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null,
            get(),
        )
    } onClose { it?.close() }
    single<AuthRepository> { get<FirebaseAuthRepository>() }
    single<FirebaseIdTokenProvider> {
        SdkFirebaseIdTokenProvider(if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null)
    }
    single<ServerAuthRepository> {
        val api = BuildConfig.FIREBASE_API_BASE_URL.takeIf(String::isNotBlank)?.let { url ->
            get<Retrofit>().newBuilder().baseUrl(url)
                .client(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS).build())
                .build().create(FirebaseAuthApi::class.java)
        }
        FirebaseServerAuthRepository(api, get())
    }
    single<GoogleSignInLauncher> { CredentialManagerGoogleSignInLauncher(get()) }
    single<CoroutineDispatcher>(named("authIo")) { Dispatchers.IO }
    factory { AuthReducer() }
    factory { AuthInteractor(get(), get(), get(named("authIo"))) }
    viewModel {
        AuthViewModel(AuthState(session = get<AuthRepository>().session.value), get<AuthReducer>(), get<AuthInteractor>())
    }
}
