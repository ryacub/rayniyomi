package eu.kanade.tachiyomi.ui.player.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoTrack(
    val id: Int,
    val name: String,
    val language: String?,
)
