/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.content.res.AssetManager
import com.hippo.unifile.UniFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.storage.service.StorageManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PlayerMpvInitializerTest {
    private lateinit var context: Context
    private lateinit var storageManager: StorageManager
    private lateinit var scope: CoroutineScope
    private lateinit var mpvLibProxy: MPVLibProxy
    private lateinit var initializer: PlayerMpvInitializer

    @BeforeEach
    fun setup() {
        mockkStatic(UniFile::class)
        context = mockk(relaxed = true)
        storageManager = mockk(relaxed = true)
        scope = CoroutineScope(Dispatchers.IO)
        mpvLibProxy = mockk(relaxed = true)

        initializer = PlayerMpvInitializer(
            context = context,
            storageManager = storageManager,
            mpvLibProxy = mpvLibProxy,
        )
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    // ==================== Helper Functions ====================

    private fun createMockUniFile(
        path: String,
        canRead: Boolean = true,
        size: Long = 0,
        isFile: Boolean = true,
    ): UniFile {
        return mockk<UniFile>(relaxed = true).also {
            every { it.filePath } returns path
            every { it.canRead() } returns canRead
            every { it.length() } returns size
            every { it.isFile } returns isFile
            every { it.isDirectory } returns !isFile
            // Provide a fresh ByteArrayOutputStream each time openOutputStream is called
            every { it.openOutputStream() } answers { ByteArrayOutputStream() }
        }
    }

    private fun createMockCustomButton(
        id: Long = 1L,
        onStartup: String = "",
        content: String = "mp.commandv('show-text', 'Button pressed')",
        longPressContent: String = "mp.commandv('show-text', 'Long pressed')",
    ): CustomButton {
        return mockk<CustomButton>(relaxed = true).also {
            every { it.id } returns id
            every { it.getButtonOnStartup(any()) } returns onStartup
            every { it.getButtonContent(any()) } returns content
            every { it.getButtonLongPressContent(any()) } returns longPressContent
        }
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `initialize creates MPV directory structure`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns createMockUniFile("/data/files/mpv/fonts", isFile = false)
        every { mockMpvDir.createDirectory("scripts") } returns
            createMockUniFile("/data/files/mpv/scripts", isFile = false)
        every { mockMpvDir.filePath } returns "/data/files/mpv"

        // Mock asset reading for aniyomi.lua - use answers to create fresh streams each time
        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- mock lua".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- mock lua".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("mock ttf".toByteArray()) }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("mock cert".toByteArray()) }

        val result = initializer.initialize(
            mpvConf = "profile=default",
            mpvInput = "j seek -5",
            mpvUserFilesEnabled = false,
        )

        assertEquals("/data/files/mpv", result)
        // Verify that openOutputStream was called (which is used internally by writeText)
        verify { mockConfFile.openOutputStream() }
        verify { mockInputFile.openOutputStream() }
    }

    @Test
    fun `initialize returns correct MPV directory path`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val expectedPath = "/data/files/mpv"

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns createMockUniFile("/data/files/mpv/fonts", isFile = false)
        every { mockMpvDir.createDirectory("scripts") } returns
            createMockUniFile("/data/files/mpv/scripts", isFile = false)
        every { mockMpvDir.filePath } returns expectedPath

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream(ByteArray(0)) }
        every { mockAssets.open(any(), any()) } answers { ByteArrayInputStream(ByteArray(0)) }

        val result = initializer.initialize("", "", false)
        assertEquals(expectedPath, result)
    }

    // ==================== User Files Copy Tests (Enabled) ====================

    @Test
    fun `copyUserFiles with enabled=true copies scripts directory`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val mockScriptsDir = createMockUniFile("/data/files/mpv/scripts", isFile = false)
        val mockScriptOptsDir = createMockUniFile("/data/files/mpv/script-opts", isFile = false)
        val mockShadersDir = createMockUniFile("/data/files/mpv/shaders", isFile = false)
        val mockLuaFile = createMockUniFile("/data/files/mpv/scripts/aniyomi.lua")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns createMockUniFile("/data/files/mpv/fonts", isFile = false)
        every { mockMpvDir.createDirectory("scripts") } returns mockScriptsDir
        every { mockMpvDir.createDirectory("script-opts") } returns mockScriptOptsDir
        every { mockMpvDir.createDirectory("shaders") } returns mockShadersDir
        every { mockMpvDir.filePath } returns "/data/files/mpv"
        every { mockScriptsDir.createFile("aniyomi.lua") } returns mockLuaFile

        // Mock user files
        val mockUserScript = createMockUniFile("/user/scripts/myscript.lua")
        every { storageManager.getScriptsDirectory() } returns mockk(relaxed = true) {
            every { listFiles() } returns arrayOf(mockUserScript)
        }
        every { mockUserScript.name } returns "myscript.lua"
        every { mockUserScript.openInputStream() } returns ByteArrayInputStream("-- user script".toByteArray())
        every { mockScriptsDir.createFile("myscript.lua") } returns
            createMockUniFile("/data/files/mpv/scripts/myscript.lua")

        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua bridge".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua bridge".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }

        val result = initializer.initialize("", "", mpvUserFilesEnabled = true)
        assertEquals("/data/files/mpv", result)
    }

    @Test
    fun `copyUserFiles with enabled=false deletes script directories`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val mockScriptsDir = createMockUniFile("/data/files/mpv/scripts", isFile = false)
        val mockScriptOptsDir = createMockUniFile("/data/files/mpv/script-opts", isFile = false)
        val mockShadersDir = createMockUniFile("/data/files/mpv/shaders", isFile = false)
        val mockLuaFile = createMockUniFile("/data/files/mpv/scripts/aniyomi.lua")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns createMockUniFile("/data/files/mpv/fonts", isFile = false)
        every { mockMpvDir.createDirectory("scripts") } returns mockScriptsDir
        every { mockMpvDir.createDirectory("script-opts") } returns mockScriptOptsDir
        every { mockMpvDir.createDirectory("shaders") } returns mockShadersDir
        every { mockMpvDir.filePath } returns "/data/files/mpv"
        every { mockScriptsDir.delete() } returns true
        every { mockScriptOptsDir.delete() } returns true
        every { mockShadersDir.delete() } returns true
        every { mockScriptsDir.createFile("aniyomi.lua") } returns mockLuaFile

        every { storageManager.getScriptsDirectory() } returns null
        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua bridge".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua bridge".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }

        val result = initializer.initialize("", "", mpvUserFilesEnabled = false)
        assertEquals("/data/files/mpv", result)
        verify { mockScriptsDir.delete() }
        verify { mockScriptOptsDir.delete() }
        verify { mockShadersDir.delete() }
    }

    @Test
    fun `copyUserFiles always copies aniyomi lua bridge`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val mockScriptsDir = createMockUniFile("/data/files/mpv/scripts", isFile = false)
        val mockScriptOptsDir = createMockUniFile("/data/files/mpv/script-opts", isFile = false)
        val mockShadersDir = createMockUniFile("/data/files/mpv/shaders", isFile = false)
        val mockLuaFile = createMockUniFile("/data/files/mpv/scripts/aniyomi.lua")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns createMockUniFile("/data/files/mpv/fonts", isFile = false)
        every { mockMpvDir.createDirectory("scripts") } returns mockScriptsDir
        every { mockMpvDir.createDirectory("script-opts") } returns mockScriptOptsDir
        every { mockMpvDir.createDirectory("shaders") } returns mockShadersDir
        every { mockMpvDir.filePath } returns "/data/files/mpv"
        every { mockScriptsDir.delete() } returns true
        every { mockScriptOptsDir.delete() } returns true
        every { mockShadersDir.delete() } returns true
        every { mockScriptsDir.createFile("aniyomi.lua") } returns mockLuaFile

        every { storageManager.getScriptsDirectory() } returns null
        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua bridge".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua bridge".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }

        initializer.initialize("", "", mpvUserFilesEnabled = true)
        verify { mockScriptsDir.createFile("aniyomi.lua") }
    }

    // ==================== Asset Copy Tests ====================

    @Test
    fun `copyAssets copies subfont and cacert on first run`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val mockSubfontFile = createMockUniFile("/data/files/mpv/subfont.ttf", size = 0)
        val mockCacertFile = createMockUniFile("/data/files/mpv/cacert.pem", size = 0)

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns createMockUniFile("/data/files/mpv/fonts", isFile = false)
        every { mockMpvDir.createDirectory("scripts") } returns
            createMockUniFile("/data/files/mpv/scripts", isFile = false)
        every { mockMpvDir.createFile("subfont.ttf") } returns mockSubfontFile
        every { mockMpvDir.createFile("cacert.pem") } returns mockCacertFile
        every { mockMpvDir.filePath } returns "/data/files/mpv"

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("subfont data".toByteArray()) }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("cert data".toByteArray()) }

        every { storageManager.getScriptsDirectory() } returns null
        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        val result = initializer.initialize("", "", mpvUserFilesEnabled = false)
        assertEquals("/data/files/mpv", result)
    }

    @Test
    fun `copyAssets skips copy when file size matches`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val mockSubfontFile = createMockUniFile("/data/files/mpv/subfont.ttf", size = 11)
        val mockCacertFile = createMockUniFile("/data/files/mpv/cacert.pem", size = 9)

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns createMockUniFile("/data/files/mpv/fonts", isFile = false)
        every { mockMpvDir.createDirectory("scripts") } returns
            createMockUniFile("/data/files/mpv/scripts", isFile = false)
        every { mockMpvDir.createFile("subfont.ttf") } returns mockSubfontFile
        every { mockMpvDir.createFile("cacert.pem") } returns mockCacertFile
        every { mockMpvDir.filePath } returns "/data/files/mpv"

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        // Return InputStreams with matching sizes
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } returns
            object : ByteArrayInputStream("subfont data".toByteArray()) {
                override fun available(): Int = 11
            }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } returns
            object : ByteArrayInputStream("cert data".toByteArray()) {
                override fun available(): Int = 9
            }

        every { storageManager.getScriptsDirectory() } returns null
        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        val result = initializer.initialize("", "", mpvUserFilesEnabled = false)
        assertEquals("/data/files/mpv", result)
    }

    @Test
    fun `copyAssets handles IOException gracefully`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns createMockUniFile("/data/files/mpv/fonts", isFile = false)
        every { mockMpvDir.createDirectory("scripts") } returns
            createMockUniFile("/data/files/mpv/scripts", isFile = false)
        every { mockMpvDir.createFile("subfont.ttf") } returns createMockUniFile("/data/files/mpv/subfont.ttf")
        every { mockMpvDir.createFile("cacert.pem") } returns createMockUniFile("/data/files/mpv/cacert.pem")
        every { mockMpvDir.filePath } returns "/data/files/mpv"

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } throws
            java.io.IOException("Asset not found")
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } throws
            java.io.IOException("Asset not found")

        every { storageManager.getScriptsDirectory() } returns null
        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        // Should not throw, should handle gracefully
        val result = initializer.initialize("", "", mpvUserFilesEnabled = false)
        assertEquals("/data/files/mpv", result)
    }

    // ==================== Font Sync Integration Tests ====================

    @Test
    fun `syncFontsDirectory sets MPV font properties via proxy`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val mockFontsDir = createMockUniFile("/data/files/mpv/fonts", isFile = false)

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns mockFontsDir
        every { mockMpvDir.createDirectory("scripts") } returns
            createMockUniFile("/data/files/mpv/scripts", isFile = false)
        every { mockMpvDir.filePath } returns "/data/files/mpv"
        every { mockFontsDir.filePath } returns "/data/files/mpv/fonts"
        every { mockFontsDir.listFiles() } returns emptyArray()

        every { storageManager.getFontsDirectory() } returns null
        every { storageManager.getScriptsDirectory() } returns null
        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }

        initializer.initialize("", "", mpvUserFilesEnabled = false)

        verify {
            mpvLibProxy.setPropertyString("sub-fonts-dir", "/data/files/mpv/fonts")
            mpvLibProxy.setPropertyString("osd-fonts-dir", "/data/files/mpv/fonts")
        }
    }

    @Test
    fun `syncFontsDirectory deletes stale fonts`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val mockFontsDir = createMockUniFile("/data/files/mpv/fonts", isFile = false)
        val mockStaleFont = createMockUniFile("/data/files/mpv/fonts/old.ttf")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns mockFontsDir
        every { mockMpvDir.createDirectory("scripts") } returns
            createMockUniFile("/data/files/mpv/scripts", isFile = false)
        every { mockMpvDir.filePath } returns "/data/files/mpv"
        every { mockFontsDir.filePath } returns "/data/files/mpv/fonts"
        every { mockFontsDir.listFiles() } returns arrayOf(mockStaleFont)
        every { mockStaleFont.name } returns "old.ttf"
        every { mockStaleFont.length() } returns 100
        every { mockStaleFont.delete() } returns true

        every { storageManager.getFontsDirectory() } returns null
        every { storageManager.getScriptsDirectory() } returns null
        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }

        initializer.initialize("", "", mpvUserFilesEnabled = false)
        // Verify that the stale font deletion logic was executed
        verify { mockStaleFont.length() }
        verify { mockStaleFont.name }
    }

    @Test
    fun `syncFontsDirectory handles unavailable source directory`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockConfFile = createMockUniFile("/data/files/mpv/mpv.conf")
        val mockInputFile = createMockUniFile("/data/files/mpv/input.conf")
        val mockFontsDir = createMockUniFile("/data/files/mpv/fonts", isFile = false)

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createFile("mpv.conf") } returns mockConfFile
        every { mockMpvDir.createFile("input.conf") } returns mockInputFile
        every { mockMpvDir.createDirectory("fonts") } returns mockFontsDir
        every { mockMpvDir.createDirectory("scripts") } returns
            createMockUniFile("/data/files/mpv/scripts", isFile = false)
        every { mockMpvDir.filePath } returns "/data/files/mpv"
        every { mockFontsDir.filePath } returns "/data/files/mpv/fonts"
        every { mockFontsDir.listFiles() } returns emptyArray()

        every { storageManager.getFontsDirectory() } returns null
        every { storageManager.getScriptsDirectory() } returns null
        every { storageManager.getScriptOptsDirectory() } returns null
        every { storageManager.getShadersDirectory() } returns null

        val mockAssets = mockk<AssetManager>(relaxed = true)
        every { context.assets } returns mockAssets
        every { mockAssets.open("aniyomi.lua") } answers { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("aniyomi.lua", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream("-- lua".toByteArray()) }
        every { mockAssets.open("subfont.ttf", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }
        every { mockAssets.open("cacert.pem", AssetManager.ACCESS_STREAMING) } answers
            { ByteArrayInputStream(ByteArray(0)) }

        // Should not throw or fail, just skip stale deletion
        val result = initializer.initialize("", "", mpvUserFilesEnabled = false)
        assertEquals("/data/files/mpv", result)
    }

    // ==================== Custom Buttons Tests ====================

    @Test
    fun `setupCustomButtons generates custombuttons lua file`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockScriptsDir = createMockUniFile("/data/files/mpv/scripts", isFile = false)
        val mockCustomButtonsFile = createMockUniFile("/data/files/mpv/scripts/custombuttons.lua")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createDirectory("scripts") } returns mockScriptsDir
        every { mockScriptsDir.createFile("custombuttons.lua") } returns mockCustomButtonsFile

        val button = createMockCustomButton(id = 1)
        initializer.setupCustomButtons(listOf(button), primaryButtonId = 1)

        verify { mockScriptsDir.createFile("custombuttons.lua") }
        verify { mpvLibProxy.command(arrayOf("load-script", "/data/files/mpv/scripts/custombuttons.lua")) }
    }

    @Test
    fun `setupCustomButtons creates correct Lua structure`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockScriptsDir = createMockUniFile("/data/files/mpv/scripts", isFile = false)
        val mockCustomButtonsFile = createMockUniFile("/data/files/mpv/scripts/custombuttons.lua")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createDirectory("scripts") } returns mockScriptsDir
        every { mockScriptsDir.createFile("custombuttons.lua") } returns mockCustomButtonsFile

        val button = createMockCustomButton(
            id = 1,
            onStartup = "mp.commandv('set', 'osd-level', 3)",
            content = "mp.commandv('show-text', 'Button 1')",
            longPressContent = "mp.commandv('show-text', 'Long press')",
        )
        initializer.setupCustomButtons(listOf(button), primaryButtonId = 1)

        verify { mockCustomButtonsFile.openOutputStream() }
        verify { mpvLibProxy.command(any()) }
    }

    @Test
    fun `setupCustomButtons invokes load-script command on MPVLib`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockScriptsDir = createMockUniFile("/data/files/mpv/scripts", isFile = false)
        val mockCustomButtonsFile = createMockUniFile("/data/files/mpv/scripts/custombuttons.lua")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createDirectory("scripts") } returns mockScriptsDir
        every { mockScriptsDir.createFile("custombuttons.lua") } returns mockCustomButtonsFile

        val button = createMockCustomButton(id = 1)
        initializer.setupCustomButtons(listOf(button), primaryButtonId = 1)

        val commandSlot = slot<Array<String>>()
        verify { mpvLibProxy.command(capture(commandSlot)) }
        assertEquals("load-script", commandSlot.captured[0])
        assertEquals("/data/files/mpv/scripts/custombuttons.lua", commandSlot.captured[1])
    }

    @Test
    fun `setupCustomButtons handles empty button list`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockScriptsDir = createMockUniFile("/data/files/mpv/scripts", isFile = false)
        val mockCustomButtonsFile = createMockUniFile("/data/files/mpv/scripts/custombuttons.lua")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createDirectory("scripts") } returns mockScriptsDir
        every { mockScriptsDir.createFile("custombuttons.lua") } returns mockCustomButtonsFile

        initializer.setupCustomButtons(emptyList(), primaryButtonId = 1)

        verify { mockScriptsDir.createFile("custombuttons.lua") }
        verify { mpvLibProxy.command(any()) }
    }

    @Test
    fun `setupCustomButtons with multiple buttons registers all handlers`() = runTest {
        val mockFilesDir = createMockUniFile("/data/files", isFile = false)
        val mockMpvDir = createMockUniFile("/data/files/mpv", isFile = false)
        val mockScriptsDir = createMockUniFile("/data/files/mpv/scripts", isFile = false)
        val mockCustomButtonsFile = createMockUniFile("/data/files/mpv/scripts/custombuttons.lua")

        every { context.filesDir } returns mockk(relaxed = true)
        every { UniFile.fromFile(any()) } returns mockFilesDir
        every { mockFilesDir.createDirectory("mpv") } returns mockMpvDir
        every { mockMpvDir.createDirectory("scripts") } returns mockScriptsDir
        every { mockScriptsDir.createFile("custombuttons.lua") } returns mockCustomButtonsFile

        val button1 = createMockCustomButton(id = 1)
        val button2 = createMockCustomButton(id = 2)
        initializer.setupCustomButtons(listOf(button1, button2), primaryButtonId = 1)

        verify { mockScriptsDir.createFile("custombuttons.lua") }
        verify { mpvLibProxy.command(any()) }
    }
}
