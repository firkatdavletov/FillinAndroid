package com.firkat.fillin.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import kotlinx.coroutines.CancellationException

/** Hosts the picker only while its screen is visible; Firebase exchange belongs to the ViewModel. */
@Composable
fun GoogleSignInEffect(viewModel: AuthViewModel, googleSignIn: GoogleSignInLauncher) {
    val context = LocalContext.current
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelCredentialRequest() }
    }
    LaunchedEffect(viewModel, googleSignIn, context) {
        viewModel.googleSignInRequests.collect { request ->
            if (!viewModel.isCredentialRequestActive(request.id)) return@collect
            try {
                viewModel.credentialReceived(request.id, googleSignIn.launch(context))
            } catch (_: GetCredentialCancellationException) {
                viewModel.credentialCancelled(request.id)
            } catch (cancelled: CancellationException) {
                viewModel.credentialCancelled(request.id)
                throw cancelled
            } catch (_: NoCredentialException) {
                viewModel.credentialFailed(request.id, AuthError.NoGoogleAccount)
            } catch (failure: AuthFailure) {
                viewModel.credentialFailed(request.id, failure.error)
            } catch (_: Exception) {
                viewModel.credentialFailed(request.id, AuthError.SignInFailed)
            }
        }
    }
}
