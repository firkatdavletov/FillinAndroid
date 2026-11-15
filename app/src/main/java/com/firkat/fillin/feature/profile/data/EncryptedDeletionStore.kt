package com.firkat.fillin.feature.profile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.firkat.fillin.feature.profile.domain.*
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun newDeletionSecret(): String = java.util.Base64.getUrlEncoder().withoutPadding()
    .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

/** Read failures deliberately fail closed: an unreadable receipt must never enable cloud exchange. */
class EncryptedDeletionStore(context: Context) : DeletionStore {
    @Serializable private data class Contents(
        val operations: List<DeletionOperation> = emptyList(),
        val blocked: Set<String> = emptySet()
    )
    private val file = AtomicFile(File(context.noBackupFilesDir, "profile-deletion"))
    private val key: SecretKey by lazy {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey("profile-deletion", null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                        "profile-deletion",
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(
                            KeyProperties.BLOCK_MODE_GCM
                        ).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
                )
            }.generateKey()
    }
    private var readFailure: Exception? = null
    private var contents = try {
        if (!file.baseFile.exists() &&
            !File(file.baseFile.path + ".bak").exists()
        ) {
            Contents()
        } else {
            val bytes = file.openRead().use { it.readBytes() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            Json.decodeFromString<Contents>(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
        }
    } catch (failure: Exception) {
        readFailure = failure
        Contents()
    }

    @Synchronized override fun operations(): List<DeletionOperation> {
        checkReadable()
        return contents.operations.toList()
    }

    @Synchronized override fun blocked(
        uid: String
    ) = readFailure != null || uid in contents.blocked

    @Synchronized override fun save(
        operation: DeletionOperation
    ) = write(
        contents.copy(
            operations = contents.operations.filterNot { it.uid == operation.uid } + operation,
            blocked =
            contents.blocked + operation.uid
        )
    )

    @Synchronized override fun block(
        uid: String
    ) = write(contents.copy(blocked = contents.blocked + uid))

    @Synchronized override fun finish(
        uid: String
    ) = write(
        contents.copy(
            operations = contents.operations.filterNot {
                it.uid ==
                    uid
            }
        )
    )
    private fun checkReadable() {
        if (readFailure != null) throw DeletionFailure("storage_unavailable")
    }
    private fun write(
        next: Contents
    ) {
        checkReadable()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val bytes = cipher.iv + cipher.doFinal(Json.encodeToString(next).toByteArray())
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
            contents = next
        } catch (
            failure: Exception
        ) {
            file.failWrite(stream)
            throw failure
        }
    }
}
