package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.util.chapter.StartAction

enum class ReaderEntry {
    Fade,
    Immediate,
    Default,
}

fun readerEntry(action: StartAction, animatorScale: Float): ReaderEntry = when (action) {
    is StartAction.FirstStart -> if (animatorScale == 0f) ReaderEntry.Immediate else ReaderEntry.Default
    is StartAction.Resume -> if (animatorScale == 0f) ReaderEntry.Immediate else ReaderEntry.Fade
    StartAction.None -> ReaderEntry.Default
}
