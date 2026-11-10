package eu.kanade.tachiyomi.ui.reader

/** The on-screen cover rectangle and image URL that the reader expands from. */
data class CoverHandoff(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val url: String,
)
