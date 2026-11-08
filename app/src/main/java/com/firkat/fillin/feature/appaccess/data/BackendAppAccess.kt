package com.firkat.fillin.feature.appaccess.data

import android.util.AtomicFile
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.feature.appaccess.domain.AppMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import java.io.File

@Serializable
internal data class AccessResponse(val mode: String)

internal interface AppAccessApi {
    @GET("/api/v4/app/access")
    suspend fun access(): Response<AccessResponse>
}

internal class BackendAppAccess(
    private val api: AppAccessApi?,
    private val backend: String,
    private val id: () -> String,
    cacheFile: File,
    private val elapsedMillis: () -> Long,
) : AppAccess {
    private val cache = AtomicFile(cacheFile)
    private val stateLock = Any()
    private var revision = 0L
    private val current = MutableStateFlow(
        if (backend.isNotBlank() && runCatching { cache.readFully().decodeToString() == cacheKey() }.getOrDefault(false)) {
            AppMode.InternalFull
        } else AppMode.OfflineFree,
    )
    override val mode = current.asStateFlow()
    private val inProgress = MutableStateFlow(false)
    override val checking = inProgress.asStateFlow()
    private val refreshLock = Mutex()
    private var lastAutomaticCheck: Long? = null

    override fun installationId() = id()

    override suspend fun refresh(manual: Boolean): Boolean = refreshLock.withLock {
        val endpoint = api ?: return false
        val now = elapsedMillis()
        if (!manual && lastAutomaticCheck?.let { now - it < 15 * 60 * 1000L } == true) return false
        lastAutomaticCheck = now
        val before = synchronized(stateLock) { revision }
        inProgress.value = true
        try {
            val response = endpoint.access()
            response.errorBody()?.close()
            val resolved = when (response.body()?.mode) {
                "OFFLINE_FREE" -> AppMode.OfflineFree
                "INTERNAL_FULL" -> AppMode.InternalFull
                else -> return false
            }
            synchronized(stateLock) {
                if (revision != before) return false
                applyMode(resolved)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            inProgress.value = false
        }
    }

    override fun revoke() = synchronized(stateLock) {
        revision++
        applyMode(AppMode.OfflineFree)
    }

    private fun applyMode(mode: AppMode) {
        if (mode == AppMode.OfflineFree) {
            current.value = mode
            cache.delete()
        } else {
            val stream = cache.startWrite()
            try {
                stream.write(cacheKey().encodeToByteArray())
                cache.finishWrite(stream)
            } catch (failure: Exception) {
                cache.failWrite(stream)
                throw failure
            }
        }
        current.value = mode
    }

    private fun cacheKey() = "${id()}\n$backend"
}
