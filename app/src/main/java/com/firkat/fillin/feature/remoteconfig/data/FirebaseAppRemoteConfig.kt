package com.firkat.fillin.feature.remoteconfig.data

import com.firkat.fillin.feature.remoteconfig.domain.AppRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.tasks.await

internal class FirebaseAppRemoteConfig(
    private val readActiveValues: () -> Map<String, FirebaseRemoteConfigValue> = ::emptyMap,
    private val fetchAndActivate: suspend () -> Unit = {}
) : AppRemoteConfig {
    @Volatile
    private var activeValues: Map<String, FirebaseRemoteConfigValue> = emptyMap()

    fun loadCache() {
        activeValues = readActiveValues()
    }

    suspend fun refresh() {
        fetchAndActivate()
        loadCache()
    }

    override fun getBoolean(
        key: String,
        defaultValue: Boolean
    ): Boolean = read(key, defaultValue, FirebaseRemoteConfigValue::asBoolean)

    override fun getString(
        key: String,
        defaultValue: String
    ): String = read(key, defaultValue, FirebaseRemoteConfigValue::asString)

    override fun getLong(
        key: String,
        defaultValue: Long
    ): Long = read(key, defaultValue, FirebaseRemoteConfigValue::asLong)

    override fun getDouble(
        key: String,
        defaultValue: Double
    ): Double = read(key, defaultValue, FirebaseRemoteConfigValue::asDouble)

    private fun <T> read(
        key: String,
        defaultValue: T,
        convert: (FirebaseRemoteConfigValue) -> T
    ): T {
        val value = activeValues[key]?.takeIf { it.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE }
            ?: return defaultValue

        return try {
            convert(value)
        } catch (_: IllegalArgumentException) {
            defaultValue
        }
    }

    companion object {
        private const val DEBUG_FETCH_INTERVAL_SECONDS = 60L
        private const val RELEASE_FETCH_INTERVAL_SECONDS = 12 * 60 * 60L

        fun create(
            debug: Boolean
        ): FirebaseAppRemoteConfig {
            val sdk by lazy { FirebaseRemoteConfig.getInstance() }
            return FirebaseAppRemoteConfig(
                readActiveValues = { sdk.all },
                fetchAndActivate = {
                    val interval = if (debug) DEBUG_FETCH_INTERVAL_SECONDS else RELEASE_FETCH_INTERVAL_SECONDS
                    val settings = FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(interval)
                        .build()
                    sdk.setConfigSettingsAsync(settings).await()
                    sdk.fetchAndActivate().await()
                }
            )
        }
    }
}
