package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.util.chapter.StartAction

enum class ReaderEntry {
    Peel,
    Fade,
    Immediate,
    Default,
}

fun readerEntry(action: StartAction, animatorScale: Float): ReaderEntry = when (action) {
    is StartAction.FirstStart -> if (animatorScale == 0f) ReaderEntry.Immediate else ReaderEntry.Peel
    is StartAction.Resume -> if (animatorScale == 0f) ReaderEntry.Immediate else ReaderEntry.Fade
    StartAction.None -> ReaderEntry.Default
}
