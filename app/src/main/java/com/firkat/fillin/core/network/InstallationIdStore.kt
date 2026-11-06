package com.firkat.fillin.core.network

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

internal class InstallationIdStore(private val directory: File) {
    private val file = File(directory, "installation-id").toPath()
    private var cachedId: String? = null

    @Synchronized
    fun get(): String {
        cachedId?.let { return it }
        val id = read() ?: create()
        cachedId = id
        return id
    }

    private fun read(): String? {
        val value = try {
            Files.readAllBytes(file).toString(Charsets.UTF_8)
        } catch (_: NoSuchFileException) {
            return null
        }
        return try {
            UUID.fromString(value).toString().takeIf { it == value }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun create(): String {
        val id = UUID.randomUUID().toString()
        // Keep the temporary file beside the target so the rename is atomic.
        val temporaryFile = File(directory, "installation-id.tmp")
        FileOutputStream(temporaryFile).use { stream ->
            stream.write(id.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        Files.move(temporaryFile.toPath(), file, ATOMIC_MOVE, REPLACE_EXISTING)
        return id
    }
}
