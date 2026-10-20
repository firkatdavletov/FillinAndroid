package com.firkat.fillin.feature.auth.di

import androidx.credentials.CredentialManager
import com.firkat.fillin.BuildConfig
import com.firkat.fillin.feature.auth.data.FirebaseAuthRepository
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.CredentialManagerGoogleSignInLauncher
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.google.firebase.auth.FirebaseAuth
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose

val authModule = module {
    single { CredentialManager.create(androidContext()) }
    single {
        FirebaseAuthRepository(
            if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null,
            get(),
        )
    } onClose { it?.close() }
    single<AuthRepository> { get<FirebaseAuthRepository>() }
    single<GoogleSignInLauncher> { CredentialManagerGoogleSignInLauncher(get()) }
    viewModel { AuthViewModel(get()) }
}
