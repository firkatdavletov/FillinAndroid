package com.firkat.fillin.feature.budgettransfer.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransferException
import com.firkat.fillin.feature.budgettransfer.domain.TransferFailure
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal class BudgetDocuments(private val resolver: ContentResolver, private val codec: BudgetCsvCodec) {
    fun read(
        document: String
    ): BudgetSnapshot = access {
        val input = resolver.openInputStream(Uri.parse(document)) ?: throw IOException()
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        InputStreamReader(input, decoder).use(codec::read)
    }

    fun write(
        document: String,
        snapshot: BudgetSnapshot
    ) = access {
        val uri = Uri.parse(document)
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException()
        output.bufferedWriter(Charsets.UTF_8).use { codec.write(snapshot, it) }
    }

    fun discard(
        document: String
    ) {
        runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(document)) }
    }

    private inline fun <T> access(
        block: () -> T
    ): T = try {
        block()
    } catch (_: CharacterCodingException) {
        throw BudgetTransferException(TransferFailure.InvalidFile)
    } catch (_: IOException) {
        throw BudgetTransferException(TransferFailure.FileAccess)
    } catch (_: SecurityException) {
        throw BudgetTransferException(TransferFailure.FileAccess)
    } catch (_: IllegalArgumentException) {
        throw BudgetTransferException(TransferFailure.FileAccess)
    }
}
