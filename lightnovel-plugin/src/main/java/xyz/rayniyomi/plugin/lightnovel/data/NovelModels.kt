package xyz.rayniyomi.plugin.lightnovel.data

import kotlinx.serialization.Serializable

@Serializable
data class NovelBook(
    val id: String,
    val title: String,
    val epubFileName: String,
    val lastReadChapter: Int = 0,
    val lastReadOffset: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NovelLibrary(
    val books: List<NovelBook> = emptyList(),
)
