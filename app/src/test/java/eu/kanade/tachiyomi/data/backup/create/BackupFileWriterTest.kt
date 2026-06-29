package eu.kanade.tachiyomi.data.backup.create

import android.net.Uri
import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class BackupFileWriterTest {

    @Test
    fun `manual backup validation failure does not open final target`() {
        val target = fakeUniFile("manual.tachibk", "previous backup".encodeToByteArray())
        val staged = fakeUniFile("staged.tachibk")
        val writer = BackupFileWriter(
            validator = { throw IllegalStateException("invalid backup") },
        )

        assertThrows(IllegalStateException::class.java) {
            writer.writeManualBackup(
                stagedFile = staged,
                targetFile = target,
                backupBytes = "new backup".encodeToByteArray(),
            )
        }

        assertArrayEquals("previous backup".encodeToByteArray(), target.bytes)
        verify(exactly = 0) { target.file.openOutputStream() }
    }

    @Test
    fun `manual backup write failure restores existing target bytes`() {
        val target = fakeUniFile(
            name = "manual.tachibk",
            initialBytes = "previous backup".encodeToByteArray(),
            failNextOutputAfterBytes = 1,
        )
        val staged = fakeUniFile("staged.tachibk")
        val writer = BackupFileWriter()

        assertThrows(RuntimeException::class.java) {
            writer.writeManualBackup(
                stagedFile = staged,
                targetFile = target,
                backupBytes = "new backup".encodeToByteArray(),
            )
        }

        assertArrayEquals("previous backup".encodeToByteArray(), target.bytes)
        verify(exactly = 2) { target.file.openOutputStream() }
    }

    @Test
    fun `raw staged file commit failure restores existing target bytes`() {
        val target = fakeUniFile(
            name = "manual.lightnovel.tachibk",
            initialBytes = "previous light novel backup".encodeToByteArray(),
            failNextOutputAfterBytes = 1,
        )
        val staged = fakeUniFile("manual.lightnovel.tachibk.tmp", "new light novel backup".encodeToByteArray())
        val writer = BackupFileWriter()

        assertThrows(RuntimeException::class.java) {
            writer.commitRawStagedFile(staged.file, target.file)
        }

        assertArrayEquals("previous light novel backup".encodeToByteArray(), target.bytes)
        verify(exactly = 2) { target.file.openOutputStream() }
    }

    @Test
    fun `restore raw file rewrites previous target bytes`() {
        val target = fakeUniFile("manual.tachibk", "new backup".encodeToByteArray())
        val writer = BackupFileWriter()

        writer.restoreRawFile(target.file, "previous backup".encodeToByteArray())

        assertArrayEquals("previous backup".encodeToByteArray(), target.bytes)
    }

    private fun fakeUniFile(
        name: String,
        initialBytes: ByteArray = ByteArray(0),
        failNextOutputAfterBytes: Int? = null,
    ): FakeUniFile {
        val file = mockk<UniFile>(relaxed = true)
        val fake = FakeUniFile(file, initialBytes)
        var failNextOutputAfterBytes = failNextOutputAfterBytes

        every { file.uri } returns mockk<Uri>(relaxed = true)
        every { file.name } returns name
        every { file.isFile } returns true
        every { file.length() } answers { fake.bytes.size.toLong() }
        every { file.openInputStream() } answers { fake.bytes.inputStream() }
        every { file.openOutputStream() } answers {
            fake.bytes = ByteArray(0)
            val failAfterBytes = failNextOutputAfterBytes
            failNextOutputAfterBytes = null
            object : ByteArrayOutputStream() {
                override fun write(b: Int) {
                    super.write(b)
                    fake.bytes = toByteArray()
                    if (failAfterBytes != null && size() >= failAfterBytes) {
                        throw RuntimeException("write failed")
                    }
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    for (index in off until off + len) {
                        write(b[index].toInt())
                    }
                }

                override fun close() {
                    fake.bytes = toByteArray()
                    super.close()
                }
            }
        }

        return fake
    }

    private class FakeUniFile(
        val file: UniFile,
        var bytes: ByteArray,
    )

    private fun BackupFileWriter.writeManualBackup(
        stagedFile: FakeUniFile,
        targetFile: FakeUniFile,
        backupBytes: ByteArray,
    ): Uri {
        return writeManualBackup(stagedFile.file, targetFile.file, backupBytes)
    }
}
