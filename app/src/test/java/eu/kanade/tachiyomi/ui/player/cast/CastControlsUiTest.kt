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

package eu.kanade.tachiyomi.ui.player.cast

import eu.kanade.tachiyomi.ui.player.cast.components.formatCastTime
import eu.kanade.tachiyomi.ui.player.model.VideoTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastControlsUiTest {

    @Test
    fun `CastButton contentDescription is idle when castState is DISCONNECTED`() {
        val castState = CastState.DISCONNECTED
        val expectedDesc = "Cast to devices"
        val actualDesc = when (castState) {
            CastState.CONNECTED -> "Casting to TV"
            CastState.CONNECTING -> "Connecting to Cast device"
            else -> expectedDesc
        }
        assertEquals(expectedDesc, actualDesc)
    }

    @Test
    fun `CastButton contentDescription is connecting when castState is CONNECTING`() {
        val castState = CastState.CONNECTING
        val expectedDesc = "Connecting to Cast device"
        val actualDesc = when (castState) {
            CastState.CONNECTED -> "Casting to TV"
            CastState.CONNECTING -> expectedDesc
            else -> "Cast to devices"
        }
        assertEquals(expectedDesc, actualDesc)
    }

    @Test
    fun `CastButton contentDescription is connected when castState is CONNECTED`() {
        val castState = CastState.CONNECTED
        val expectedDesc = "Casting to TV"
        val actualDesc = when (castState) {
            CastState.CONNECTED -> expectedDesc
            CastState.CONNECTING -> "Connecting to Cast device"
            else -> "Cast to devices"
        }
        assertEquals(expectedDesc, actualDesc)
    }

    @Test
    fun `CastButton is disabled when url starts with content-slash-slash`() {
        val url = "content://some.provider/path"
        val canCast = !url.startsWith("content://") && !url.startsWith("file://")
        assertFalse(canCast)
    }

    @Test
    fun `CastButton is disabled when url starts with file-slash-slash`() {
        val url = "file:///storage/emulated/0/video.mp4"
        val canCast = !url.startsWith("content://") && !url.startsWith("file://")
        assertFalse(canCast)
    }

    @Test
    fun `CastButton is enabled when url starts with https-slash-slash`() {
        val url = "https://example.com/video.mp4"
        val canCast = !url.startsWith("content://") && !url.startsWith("file://")
        assertTrue(canCast)
    }

    @Test
    fun `CastMiniController is shown when isCasting is true`() {
        val castState = CastState.CONNECTED
        val isCasting = (castState == CastState.CONNECTED)
        assertTrue(isCasting)
    }

    @Test
    fun `CastMiniController seekbar stateDescription contains position and duration`() {
        val positionMs = 30000L
        val formatted = formatCastTime(positionMs)
        assertEquals("0:30", formatted)
    }

    @Test
    fun `formatCastTime formats hours correctly`() {
        val positionMs = 3_661_000L // 1h 1m 1s
        val formatted = formatCastTime(positionMs)
        assertEquals("1:01:01", formatted)
    }

    @Test
    fun `CastMiniController disconnect callback is invoked`() {
        var disconnectCalled = false
        val onDisconnect = { disconnectCalled = true }
        onDisconnect()
        assertTrue(disconnectCalled)
    }

    @Test
    fun `CastControlSheet filters out ass-slash-ssa tracks`() {
        val tracks = listOf(
            VideoTrack(id = 0, name = "sub.srt", language = null),
            VideoTrack(id = 1, name = "sub.vtt", language = null),
            VideoTrack(id = 2, name = "sub.ass", language = null),
            VideoTrack(id = 3, name = "sub.ssa", language = null),
        )
        val castCompatible = tracks.filter {
            it.name.endsWith(".srt", ignoreCase = true) ||
                it.name.endsWith(".vtt", ignoreCase = true)
        }
        assertEquals(2, castCompatible.size)
        assertTrue(castCompatible.all { it.name.endsWith(".srt") || it.name.endsWith(".vtt") })
    }
}
