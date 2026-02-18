package xyz.rayniyomi.plugin.lightnovel.reader

internal object ReaderProgressMapper {
    fun offsetToScrollY(charOffset: Int, chapterLength: Int, maxScrollY: Int): Int {
        if (chapterLength <= 0 || maxScrollY <= 0) return 0
        val boundedOffset = charOffset.coerceIn(0, chapterLength)
        val ratio = boundedOffset.toFloat() / chapterLength.toFloat()
        return (ratio * maxScrollY).toInt().coerceIn(0, maxScrollY)
    }

    fun scrollYToOffset(scrollY: Int, chapterLength: Int, maxScrollY: Int): Int {
        if (chapterLength <= 0) return 0
        if (maxScrollY <= 0) return 0
        val boundedScroll = scrollY.coerceIn(0, maxScrollY)
        val ratio = boundedScroll.toFloat() / maxScrollY.toFloat()
        return (ratio * chapterLength).toInt().coerceIn(0, chapterLength)
    }
}
