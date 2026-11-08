package com.firkat.fillin.feature.auth.data

import com.google.firebase.auth.*
import kotlinx.coroutines.tasks.await

/** SDK boundary; tests can model partial success and delayed callbacks without a Firebase app. */
interface FirebaseAuthGateway {
    val user: FirebaseUserSnapshot?
    fun observe(listener: () -> Unit): AutoCloseable
    suspend fun signIn(email: String, password: String)
    suspend fun create(email: String, password: String)
    suspend fun google(token: String)
    suspend fun verification()
    suspend fun reset(email: String)
    suspend fun reload()
    suspend fun refresh()
    suspend fun linkPassword(password: String)
    suspend fun linkGoogle(token: String)
    suspend fun reauthenticatePassword(password: String)
    suspend fun reauthenticateGoogle(token: String)
    fun signOut()
}

data class FirebaseUserSnapshot(val uid: String, val name: String?, val email: String?, val verified: Boolean, val providers: Set<String>, val anonymous: Boolean, val signInProvider: String? = null)

class SdkFirebaseAuthGateway(private val auth: FirebaseAuth) : FirebaseAuthGateway {
    @Volatile private var providerByUid: Pair<String, String?>? = null
    override val user get() = auth.currentUser?.let { FirebaseUserSnapshot(it.uid, it.displayName, it.email, it.isEmailVerified, it.providerData.map { p -> p.providerId }.toSet(), it.isAnonymous, providerByUid?.takeIf { p -> p.first == it.uid }?.second) }
    private fun current() = requireNotNull(auth.currentUser)
    override fun observe(listener: () -> Unit): AutoCloseable {
        val callback = FirebaseAuth.AuthStateListener {
            listener()
            val restored = auth.currentUser
            restored?.getIdToken(false)?.addOnSuccessListener { token ->
                if (auth.currentUser?.uid == restored.uid) {
                    providerByUid = restored.uid to token.signInProvider
                    listener()
                }
            }
        }
        auth.addAuthStateListener(callback)
        return AutoCloseable { auth.removeAuthStateListener(callback) }
    }
    override suspend fun signIn(email: String, password: String) { auth.signInWithEmailAndPassword(email, password).await(); providerByUid = current().uid to "password" }
    override suspend fun create(email: String, password: String) { auth.createUserWithEmailAndPassword(email, password).await(); providerByUid = current().uid to "password" }
    override suspend fun google(token: String) { auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await(); providerByUid = current().uid to "google.com" }
    override suspend fun verification() { auth.setLanguageCode("ru"); current().sendEmailVerification().await() }
    override suspend fun reset(email: String) { auth.setLanguageCode("ru"); auth.sendPasswordResetEmail(email).await() }
    override suspend fun reload() { current().reload().await() }
    override suspend fun refresh() { current().getIdToken(true).await() }
    override suspend fun linkPassword(password: String) = link(EmailAuthProvider.getCredential(requireNotNull(current().email), password))
    override suspend fun linkGoogle(token: String) = link(GoogleAuthProvider.getCredential(token, null))
    private suspend fun link(credential: AuthCredential) {
        try { current().linkWithCredential(credential).await() }
        catch (e: FirebaseAuthException) { if (e.errorCode != "ERROR_PROVIDER_ALREADY_LINKED") throw e }
        // Caller reloads and refreshes even when Firebase reports an already linked provider.
    }
    override suspend fun reauthenticatePassword(password: String) { current().reauthenticate(EmailAuthProvider.getCredential(requireNotNull(current().email), password)).await() }
    override suspend fun reauthenticateGoogle(token: String) { current().reauthenticate(GoogleAuthProvider.getCredential(token, null)).await() }
    override fun signOut() = auth.signOut()
}
