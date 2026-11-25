package com.firkat.fillin.feature.remoteconfig.data

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAppRemoteConfigTest {
    @Test
    fun `unconfigured client uses caller defaults`() = runTest {
        val config = FirebaseAppRemoteConfig()
        config.refresh()

        assertTrue(config.getBoolean("missing", true))
        assertEquals("local", config.getString("missing", "local"))
        assertEquals(7L, config.getLong("missing", 7L))
        assertEquals(2.5, config.getDouble("missing", 2.5), 0.0)
    }

    @Test
    fun `remote empty and zero values override defaults while invalid and missing values fall back`() = runTest {
        val config = FirebaseAppRemoteConfig(
            readActiveValues = {
                mapOf(
                    "disabled" to TestValue("false"),
                    "zero" to TestValue("0"),
                    "empty" to TestValue(""),
                    "invalid" to TestValue("invalid"),
                    "sdk_default" to TestValue("sdk", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT)
                )
            }
        )
        config.refresh()

        assertFalse(config.getBoolean("disabled", true))
        assertEquals(0L, config.getLong("zero", 7L))
        assertEquals(0.0, config.getDouble("zero", 2.5), 0.0)
        assertEquals("", config.getString("empty", "local"))
        assertTrue(config.getBoolean("invalid", true))
        assertEquals(7L, config.getLong("invalid", 7L))
        assertEquals(2.5, config.getDouble("invalid", 2.5), 0.0)
        assertEquals("local", config.getString("missing", "local"))
        assertEquals("local", config.getString("sdk_default", "local"))
    }

    @Test
    fun `persisted values are available during a fetch and after it fails`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val completeFetch = CompletableDeferred<Unit>()
        val config = FirebaseAppRemoteConfig(
            readActiveValues = { mapOf("message" to TestValue("cached")) },
            fetchAndActivate = {
                fetchStarted.complete(Unit)
                completeFetch.await()
            }
        )
        config.loadCache()
        val failure = CompletableDeferred<Throwable?>()
        launch { failure.complete(runCatching { config.refresh() }.exceptionOrNull()) }

        fetchStarted.await()
        assertEquals("cached", config.getString("message", "local"))
        completeFetch.completeExceptionally(java.io.IOException("Offline"))
        assertTrue(failure.await() is java.io.IOException)
        assertEquals("cached", config.getString("message", "local"))
    }

    @Test
    fun `successful activation replaces the snapshot including removed keys`() = runTest {
        var values = mapOf("message" to TestValue("cached"), "removed" to TestValue("old"))
        val config = FirebaseAppRemoteConfig(
            readActiveValues = { values },
            fetchAndActivate = { values = mapOf("message" to TestValue("updated")) }
        )
        config.refresh()

        assertEquals("updated", config.getString("message", "local"))
        assertEquals("local", config.getString("removed", "local"))
    }

    private class TestValue(
        private val raw: String,
        private val valueSource: Int = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
    ) : FirebaseRemoteConfigValue {
        override fun asBoolean(): Boolean = raw.toBooleanStrict()
        override fun asString(): String = raw
        override fun asLong(): Long = raw.toLong()
        override fun asDouble(): Double = raw.toDouble()
        override fun asByteArray(): ByteArray = raw.encodeToByteArray()
        override fun getSource(): Int = valueSource
    }
}
