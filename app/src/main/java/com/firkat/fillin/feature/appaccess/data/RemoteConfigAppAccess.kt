package com.firkat.fillin.feature.appaccess.data

import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.feature.appaccess.domain.AppMode
import com.firkat.fillin.feature.remoteconfig.data.FirebaseAppRemoteConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RemoteConfigAppAccess(
    private val remoteConfig: FirebaseAppRemoteConfig,
    configured: Boolean,
    private val logFailure: (Exception) -> Unit
) : AppAccess {
    private val current = MutableStateFlow(AppMode.OfflineFree)
    override val mode = current.asStateFlow()
    private val cacheLoaded = MutableStateFlow(!configured)
    override val initialized = cacheLoaded.asStateFlow()
    private val startupLock = Mutex()

    suspend fun loadAtStartup(
        fetchEnabled: Boolean
    ) = startupLock.withLock {
        if (cacheLoaded.value) return@withLock
        if (tryFirebase { remoteConfig.loadCache() }) publishMode()
        cacheLoaded.value = true
        if (fetchEnabled && tryFirebase { remoteConfig.refresh() }) publishMode()
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun tryFirebase(
        action: () -> Unit
    ): Boolean = try {
        action()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        logFailure(failure)
        false
    }

    private fun publishMode() {
        current.value = if (remoteConfig.getString("remote_access", "false").toBooleanStrictOrNull() == true) {
            AppMode.InternalFull
        } else {
            AppMode.OfflineFree
        }
    }
}
