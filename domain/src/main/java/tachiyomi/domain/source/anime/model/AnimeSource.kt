package tachiyomi.domain.source.anime.model

import tachiyomi.domain.source.manga.model.SourceHealthStatus

data class AnimeSource(
    val id: Long,
    val lang: String,
    val name: String,
    val supportsLatest: Boolean,
    val isStub: Boolean,
    val pin: Pins = Pins.unpinned,
    val isUsedLast: Boolean = false,
    val healthStatus: SourceHealthStatus = SourceHealthStatus.UNKNOWN,
) {

    val visualName: String
        get() = when {
            lang.isEmpty() -> name
            else -> "$name (${lang.uppercase()})"
        }

    val key: () -> String = {
        when {
            isUsedLast -> "$id-lastused"
            else -> "$id"
        }
    }
}
