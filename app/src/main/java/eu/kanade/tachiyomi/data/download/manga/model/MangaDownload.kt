package eu.kanade.tachiyomi.data.download.manga.model

import eu.kanade.tachiyomi.data.download.model.DownloadBlockedReason
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadPriority
import eu.kanade.tachiyomi.data.download.model.DownloadStatusSnapshot
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicLong

data class MangaDownload(
    val source: HttpSource,
    val manga: Manga,
    val chapter: Chapter,
    var priority: DownloadPriority = DownloadPriority.NORMAL,
) : DownloadStatusSnapshot {

    @Transient
    private val pagesStateFlow = MutableStateFlow<List<Page>?>(null)

    var pages: List<Page>? = null
        set(value) {
            field = value
            pagesStateFlow.value = value
        }

    val totalProgress: Int
        get() = pages?.sumOf(Page::progress) ?: 0

    val downloadedImages: Int
        get() = pages?.count { it.status == Page.State.READY } ?: 0

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    private val _displayStatusFlow = MutableStateFlow(DownloadDisplayStatus.PREPARING)

    @Transient
    val displayStatusFlow = _displayStatusFlow.asStateFlow()
    override var displayStatus: DownloadDisplayStatus
        get() = _displayStatusFlow.value
        set(value) {
            _displayStatusFlow.value = value
        }

    @Transient
    var blockedReason: DownloadBlockedReason? = null

    override val isRunningTransfer: Boolean
        get() = status == State.DOWNLOADING

    @Transient
    private val lastProgressAtAtomic = AtomicLong(0L)
    override var lastProgressAt: Long
        get() = lastProgressAtAtomic.get()
        set(value) {
            lastProgressAtAtomic.set(value)
        }

    @Transient
    override var retryAttempt: Int = 0

    @Transient
    var lastErrorCode: String? = null

    @Transient
    override var lastErrorReason: String? = null

    @Transient
    val progressFlow = pagesStateFlow
        .flatMapLatest { pages ->
            pages
                ?.takeUnless { it.isEmpty() }
                ?.let { combine(it.map(Page::progressFlow)) { progress -> progress.average().toInt() } }
                ?: flowOf(0)
        }
        .distinctUntilChanged()
        .debounce(50)

    val progress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).average().toInt()
        }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        suspend fun fromChapterId(
            chapterId: Long,
            getChapter: GetChapter = Injekt.get(),
            getManga: GetManga = Injekt.get(),
            sourceManager: MangaSourceManager = Injekt.get(),
        ): MangaDownload? {
            val chapter = getChapter.await(chapterId) ?: return null
            val manga = getManga.await(chapter.mangaId) ?: return null
            val source = sourceManager.get(manga.source) as? HttpSource ?: return null

            return MangaDownload(source, manga, chapter)
        }
    }
}
