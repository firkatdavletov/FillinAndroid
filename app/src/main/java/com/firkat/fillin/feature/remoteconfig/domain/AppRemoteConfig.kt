package com.firkat.fillin.feature.remoteconfig.domain

/** Reads active parameters without disk or network access. */
interface AppRemoteConfig {
    fun getBoolean(
        key: String,
        defaultValue: Boolean
    ): Boolean

    fun getString(
        key: String,
        defaultValue: String
    ): String

    fun getLong(
        key: String,
        defaultValue: Long
    ): Long

    fun getDouble(
        key: String,
        defaultValue: Double
    ): Double
}
