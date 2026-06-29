package eu.kanade.tachiyomi.data.backup.create

import android.net.Uri
import com.hippo.unifile.UniFile
import okio.buffer
import okio.gzip
import okio.sink
import okio.source
import java.io.FileOutputStream

class BackupFileWriter(
    private val validator: (Uri) -> Unit = {},
) {

    fun writeAutoBackup(file: UniFile, backupBytes: ByteArray): Uri {
        writeCompressedBackup(file, backupBytes)
        val fileUri = file.uri
        validator(fileUri)
        return fileUri
    }

    fun writeManualBackup(
        stagedFile: UniFile,
        targetFile: UniFile,
        backupBytes: ByteArray,
    ): Uri {
        writeCompressedBackup(stagedFile, backupBytes)
        validator(stagedFile.uri)
        commitStagedBackup(stagedFile, targetFile)
        return targetFile.uri
    }

    fun writeRawStagedFile(file: UniFile, bytes: ByteArray) {
        file.openOutputStream().use { outputStream ->
            (outputStream as? FileOutputStream)?.channel?.truncate(0)
            outputStream.write(bytes)
        }
    }

    fun commitRawStagedFile(stagedFile: UniFile, targetFile: UniFile): Uri {
        commitStagedBackup(stagedFile, targetFile)
        return targetFile.uri
    }

    fun readBytesOrNull(file: UniFile): ByteArray? {
        return readBytes(file)
    }

    fun restoreRawFile(file: UniFile, bytes: ByteArray) {
        restoreTarget(file, bytes)
    }

    private fun writeCompressedBackup(file: UniFile, backupBytes: ByteArray) {
        file.openOutputStream().use { outputStream ->
            (outputStream as? FileOutputStream)?.channel?.truncate(0)
            outputStream.sink().gzip().buffer().use {
                it.write(backupBytes)
            }
        }
    }

    private fun commitStagedBackup(stagedFile: UniFile, targetFile: UniFile) {
        val previousBytes = readBytes(targetFile)

        try {
            targetFile.openOutputStream().use { outputStream ->
                (outputStream as? FileOutputStream)?.channel?.truncate(0)
                stagedFile.openInputStream().source().buffer().use { source ->
                    outputStream.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }
            }
        } catch (e: Exception) {
            if (previousBytes != null) {
                restoreTarget(targetFile, previousBytes)
            }
            throw e
        }
    }

    private fun readBytes(file: UniFile): ByteArray? {
        return runCatching {
            file.openInputStream().use { it.readBytes() }
        }.getOrNull()
    }

    private fun restoreTarget(targetFile: UniFile, previousBytes: ByteArray) {
        runCatching {
            targetFile.openOutputStream().use { outputStream ->
                (outputStream as? FileOutputStream)?.channel?.truncate(0)
                outputStream.write(previousBytes)
            }
        }
    }
}
