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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.content.res.AssetManager
import com.hippo.unifile.UniFile
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.storage.service.StorageManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Handles MPV initialization including file I/O setup for configuration, scripts, and fonts.
 * Extracted from PlayerActivity to reduce side-effect complexity.
 */
internal class PlayerMpvInitializer(
    private val context: Context,
    private val storageManager: StorageManager,
    private val scope: CoroutineScope,
) {
    companion object {
        const val MPV_DIR = "mpv"
        private const val MPV_FONTS_DIR = "fonts"
        private const val MPV_SCRIPTS_DIR = "scripts"
        private const val MPV_SCRIPTS_OPTS_DIR = "script-opts"
        private const val MPV_SHADERS_DIR = "shaders"
    }

    private fun UniFile.writeText(text: String) {
        this.openOutputStream().use {
            it.write(text.toByteArray())
        }
    }

    /**
     * Initialize MPV configuration and file structure.
     * Must be called on a background thread/scope.
     */
    suspend fun initialize(
        mpvConf: String,
        mpvInput: String,
        mpvUserFilesEnabled: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val mpvDir = UniFile.fromFile(context.filesDir)!!.createDirectory(MPV_DIR)!!

        val mpvConfFile = mpvDir.createFile("mpv.conf")!!
        mpvConfFile.writeText(mpvConf)

        val mpvInputFile = mpvDir.createFile("input.conf")!!
        mpvInputFile.writeText(mpvInput)

        copyUserFiles(mpvDir, mpvUserFilesEnabled)
        copyAssets(mpvDir)
        copyFontsDirectory(mpvDir)

        return@withContext mpvDir.filePath!!
    }

    private fun copyUserFiles(mpvDir: UniFile, enabled: Boolean) {
        // First, delete all present scripts
        val scriptsDir = { mpvDir.createDirectory(MPV_SCRIPTS_DIR) }
        val scriptOptsDir = { mpvDir.createDirectory(MPV_SCRIPTS_OPTS_DIR) }
        val shadersDir = { mpvDir.createDirectory(MPV_SHADERS_DIR) }

        scriptsDir()?.delete()
        scriptOptsDir()?.delete()
        shadersDir()?.delete()

        // Then, copy the user files from the Aniyomi directory
        if (enabled) {
            storageManager.getScriptsDirectory()?.listFiles()?.forEach { file ->
                val outFile = scriptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getScriptOptsDirectory()?.listFiles()?.forEach { file ->
                val outFile = scriptOptsDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
            storageManager.getShadersDirectory()?.listFiles()?.forEach { file ->
                val outFile = shadersDir()?.createFile(file.name)
                outFile?.let {
                    file.openInputStream().copyTo(it.openOutputStream())
                }
            }
        }

        // Copy over the bridge file
        val luaFile = scriptsDir()?.createFile("aniyomi.lua")
        val luaBridge = context.assets.open("aniyomi.lua")
        luaFile?.openOutputStream()?.bufferedWriter()?.use { scriptLua ->
            luaBridge.bufferedReader().use { scriptLua.write(it.readText()) }
        }
    }

    private fun copyAssets(mpvDir: UniFile) {
        val assetManager = context.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = mpvDir.createFile(filename)!!
                // Note that .available() officially returns an *estimated* number of bytes available
                // this is only true for generic streams, asset streams return the full file size
                if (outFile.length() == ins.available().toLong()) {
                    logcat(LogPriority.VERBOSE) { "Skipping copy of asset file (exists same size): $filename" }
                    continue
                }
                out = outFile.openOutputStream()
                ins.copyTo(out)
                logcat(LogPriority.WARN) { "Copied asset file: $filename" }
            } catch (e: IOException) {
                logcat(LogPriority.ERROR, e) { "Failed to copy asset file: $filename" }
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    private fun copyFontsDirectory(mpvDir: UniFile) {
        // TODO: I think this is a bad hack.
        //  We need to find a way to let MPV directly access our fonts directory.
        scope.launchIO {
            val fontsDirectory = mpvDir.createDirectory(MPV_FONTS_DIR)!!

            storageManager.getFontsDirectory()?.listFiles()?.forEach { font ->
                val outFile = fontsDirectory.createFile(font.name)
                outFile?.let {
                    font.openInputStream().copyTo(it.openOutputStream())
                }
            }

            MPVLib.setPropertyString("sub-fonts-dir", fontsDirectory.filePath!!)
            MPVLib.setPropertyString("osd-fonts-dir", fontsDirectory.filePath!!)
        }
    }

    /**
     * Setup custom buttons for MPV integration.
     * Must be called on a background thread/scope.
     */
    suspend fun setupCustomButtons(buttons: List<CustomButton>, primaryButtonId: Long) = withContext(Dispatchers.IO) {
        val scriptsDir = {
            UniFile.fromFile(context.filesDir)
                ?.createDirectory(MPV_DIR)
                ?.createDirectory(MPV_SCRIPTS_DIR)
        }

        val customButtonsContent = buildString {
            append(
                """
                    local lua_modules = mp.find_config_file('scripts')
                    if lua_modules then
                        package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '${scriptsDir()!!.filePath}' .. '/?.lua'
                    end
                    local aniyomi = require 'aniyomi'
                """.trimIndent(),
            )

            buttons.forEach { button ->
                append(
                    """
                        ${button.getButtonOnStartup(primaryButtonId)}
                        function button${button.id}()
                            ${button.getButtonContent(primaryButtonId)}
                        end
                        mp.register_script_message('call_button_${button.id}', button${button.id})
                        function button${button.id}long()
                            ${button.getButtonLongPressContent(primaryButtonId)}
                        end
                        mp.register_script_message('call_button_${button.id}_long', button${button.id}long)
                    """.trimIndent(),
                )
            }
        }

        val file = scriptsDir()?.createFile("custombuttons.lua")
        file?.openOutputStream()?.bufferedWriter()?.use {
            it.write(customButtonsContent)
        }

        file?.let {
            MPVLib.command(arrayOf("load-script", it.filePath))
        }
    }
}
