package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.transition.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.transition.platform.MaterialContainerTransform
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.core.util.ifMangaSourcesLoaded
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.OrientationSelectDialog
import eu.kanade.presentation.reader.PageIndicatorText
import eu.kanade.presentation.reader.ReaderContentOverlay
import eu.kanade.presentation.reader.ReaderPageActionsDialog
import eu.kanade.presentation.reader.ReadingModeSelectDialog
import eu.kanade.presentation.reader.appbars.ReaderAppBars
import eu.kanade.presentation.reader.settings.ReaderSettingsDialog
import eu.kanade.presentation.util.FoldOcclusionType
import eu.kanade.presentation.util.FoldOrientation
import eu.kanade.presentation.util.FoldState
import eu.kanade.presentation.util.ReaderFoldState
import eu.kanade.presentation.util.readerFoldStateFrom
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import eu.kanade.tachiyomi.util.system.honorsOrientationRequests
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderActivity : BaseActivity() {

    companion object {
        private const val KEY_SHOW_AUTO_SCROLL_PANEL = "reader_show_auto_scroll_panel"
        private const val KEY_IS_AUTO_SCROLL_RUNNING = "reader_is_auto_scroll_running"

        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    private val readerPreferences = Injekt.get<ReaderPreferences>()
    private val preferences = Injekt.get<BasePreferences>()

    private lateinit var rootView: FrameLayout
    private lateinit var readerContainer: FrameLayout
    private lateinit var viewerContainer: FrameLayout
    private lateinit var pageNumber: ComposeView
    private lateinit var dialogRoot: ComposeView
    private lateinit var navigationOverlay: ReaderNavigationOverlayView

    val viewModel by viewModels<ReaderViewModel>()

    private val hasCutout by lazy { hasDisplayCutout() }

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    private var menuToggleToast: Toast? = null
    private var readingModeToast: Toast? = null
    private val displayRefreshHost = DisplayRefreshHost()

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, rootView) }

    private var loadingIndicator: ReaderProgressIndicator? = null

    var isScrollingThroughPages = false
        private set
    private var showAutoScrollPanel by mutableStateOf(false)
    private var isAutoScrollRunning by mutableStateOf(false)
    private var autoScrollSpeedTenths by mutableIntStateOf(readerPreferences.webtoonAutoScrollSpeedTenths().get())

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        registerSecureActivity(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }

        super.onCreate(savedInstanceState)

        setContentView(R.layout.reader_activity)
        rootView = findViewById(android.R.id.content)
        readerContainer = findViewById(R.id.reader_container)
        viewerContainer = findViewById(R.id.viewer_container)
        pageNumber = findViewById(R.id.page_number)
        dialogRoot = findViewById(R.id.dialog_root)
        navigationOverlay = findViewById(R.id.navigation_overlay)
        showAutoScrollPanel = savedInstanceState?.getBoolean(KEY_SHOW_AUTO_SCROLL_PANEL, false) ?: false
        isAutoScrollRunning = savedInstanceState?.getBoolean(KEY_IS_AUTO_SCROLL_RUNNING, false) ?: false

        viewModel.initReaderConfig(isNightMode())

        if (viewModel.needsInit()) {
            val manga = intent.extras?.getLong("manga", -1) ?: -1L
            val chapter = intent.extras?.getLong("chapter", -1) ?: -1L
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            NotificationReceiver.dismissNotification(
                this,
                manga.hashCode(),
                Notifications.ID_NEW_CHAPTERS,
            )

            lifecycleScope.launchNonCancellable {
                val initResult = viewModel.init(manga, chapter)
                if (!initResult.getOrDefault(false)) {
                    val exception = initResult.exceptionOrNull() ?: IllegalStateException(
                        "Unknown err",
                    )
                    withUIContext {
                        setInitialChapterError(exception)
                    }
                }
            }
        }

        config = ReaderConfig()
        initializeMenu()

        // Finish when incognito mode is disabled
        preferences.incognitoMode().changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        readerPreferences.webtoonAutoScrollSpeedTenths().changes()
            .onEach { speed ->
                autoScrollSpeedTenths = speed
                (viewModel.state.value.viewer as? WebtoonViewer)?.setAutoScrollSpeedTenths(speed)
            }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderEvent.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    ReaderEvent.PageChanged -> {
                        displayRefreshHost.flash()
                    }
                    is ReaderEvent.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderEvent.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderEvent.ShareImage -> {
                        onShareImageResult(event.uri, event.page)
                    }
                    is ReaderEvent.CopyImage -> {
                        onCopyImageResult(event.uri)
                    }
                    is ReaderEvent.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
            .launchIn(lifecycleScope)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@ReaderActivity)
                    .windowLayoutInfo(this@ReaderActivity)
                    .map(::readerFoldStateFrom)
                    .distinctUntilChanged()
                    .collect(viewModel::setFoldState)
            }
        }

        // Single source of truth for the tabletop upper-region constraint.
        // updateViewer() replaces the viewer on the main thread, and the next
        // fold-state emission applies the constraint to the current viewer.
        viewModel.state
            .map { it.foldState }
            .distinctUntilChanged()
            .onEach(::updateViewerForTabletopPosture)
            .launchIn(lifecycleScope)

        // Close the startup gap before the observer's first emission.
        updateViewerForTabletopPosture(viewModel.state.value.foldState)

        // Single source of truth for the vertical-hinge inset constraint.
        // updateViewer() replaces the viewer on the main thread, so it
        // reapplies the insets to the new viewer right after adding it.
        viewModel.state
            .map { it.foldState }
            .distinctUntilChanged()
            .onEach(::updateViewerForVerticalHinge)
            .launchIn(lifecycleScope)

        // Close the startup gap before the observer's first emission.
        updateViewerForVerticalHinge(viewModel.state.value.foldState)
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewModel.state.value.viewer?.destroy()
        config = null
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_SHOW_AUTO_SCROLL_PANEL, showAutoScrollPanel)
        outState.putBoolean(KEY_IS_AUTO_SCROLL_RUNNING, isAutoScrollRunning)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        viewModel.flushReadTimer()
        (viewModel.state.value.viewer as? WebtoonViewer)?.pauseAutoScroll()
        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        viewModel.restartReadTimer()
        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(viewModel.state.value.menuVisible)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        viewModel.state.value.assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        viewModel.onActivityFinish()
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N) {
            loadNextChapter()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            loadPreviousChapter()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewModel.state.value.viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = viewModel.state.value.viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    private fun initializeMenu() {
        pageNumber.setComposeContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val showPageNumber by viewModel.readerPreferences.showPageNumber().collectAsStateWithLifecycle()

            if (!state.menuVisible && showPageNumber) {
                val hingeInsets = hingeInsetsDp(state.foldState)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = hingeInsets?.left ?: 0.dp,
                            end = hingeInsets?.right ?: 0.dp,
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    PageIndicatorText(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                    )
                }
            }
        }

        dialogRoot.setComposeContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val settingsScreenModel = remember {
                ReaderSettingsScreenModel(
                    readerState = viewModel.state,
                    hasDisplayCutout = hasCutout,
                    onChangeReadingMode = viewModel::setMangaReadingMode,
                    onChangeOrientation = viewModel::setMangaOrientationType,
                )
            }

            if (!ifMangaSourcesLoaded()) {
                return@setComposeContent
            }

            val isHttpSource = viewModel.getSource() is HttpSource
            val isFullscreen by readerPreferences.fullscreen().collectAsStateWithLifecycle()
            val flashOnPageChange by readerPreferences.flashOnPageChange().collectAsStateWithLifecycle()

            val colorOverlayEnabled by readerPreferences.colorFilter().collectAsStateWithLifecycle()
            val colorOverlay by readerPreferences.colorFilterValue().collectAsStateWithLifecycle()
            val colorOverlayMode by readerPreferences.colorFilterMode().collectAsStateWithLifecycle()
            val colorOverlayBlendMode = remember(colorOverlayMode) {
                ReaderPreferences.ColorFilterMode.getOrNull(colorOverlayMode)?.second
            }

            val cropBorderPaged by readerPreferences.cropBorders().collectAsStateWithLifecycle()
            val cropBorderWebtoon by readerPreferences.cropBordersWebtoon().collectAsStateWithLifecycle()
            val isPagerType = ReadingMode.isPagerType(viewModel.getMangaReadingMode())
            val cropEnabled = if (isPagerType) cropBorderPaged else cropBorderWebtoon

            ReaderContentOverlay(
                brightness = state.brightnessOverlayValue,
                color = colorOverlay.takeIf { colorOverlayEnabled },
                colorBlendMode = colorOverlayBlendMode,
            )

            val orientationControlEnabled = honorsOrientationRequests(resources.configuration)

            ReaderAppBars(
                visible = state.menuVisible,
                fullscreen = isFullscreen,
                hingeInsets = hingeInsetsDp(state.foldState),
                tabletopConstraints = tabletopControlsConstraintsDp(state.foldState),
                mangaTitle = state.manga?.title,
                chapterTitle = state.currentChapter?.chapter?.name,
                navigateUp = onBackPressedDispatcher::onBackPressed,
                onClickTopAppBar = ::openMangaScreen,
                bookmarked = state.bookmarked,
                onToggleBookmarked = viewModel::toggleChapterBookmark,
                onOpenInWebView = ::openChapterInWebView.takeIf { isHttpSource },
                onOpenInBrowser = ::openChapterInBrowser.takeIf { isHttpSource },
                onShare = ::shareChapter.takeIf { isHttpSource },

                viewer = state.viewer,

                onNextChapter = ::loadNextChapter,
                enabledNext = state.viewerChapters?.nextChapter != null,
                onPreviousChapter = ::loadPreviousChapter,
                enabledPrevious = state.viewerChapters?.prevChapter != null,
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                onPageIndexChange = {
                    isScrollingThroughPages = true
                    moveToPageIndex(it)
                },

                readingMode = ReadingMode.fromPreference(
                    viewModel.getMangaReadingMode(resolveDefault = false),
                ),
                onClickReadingMode = viewModel::openReadingModeSelectDialog,
                orientation = ReaderOrientation.fromPreference(
                    viewModel.getMangaOrientation(resolveDefault = false),
                ),
                onClickOrientation = viewModel::openOrientationModeSelectDialog,
                orientationControlEnabled = orientationControlEnabled,
                cropEnabled = cropEnabled,
                onClickCropBorder = {
                    val enabled = viewModel.toggleCropBorders()
                    menuToggleToast?.cancel()
                    menuToggleToast = toast(if (enabled) MR.strings.on else MR.strings.off)
                },
                hasTranslation = state.hasTranslation,
                translationState = state.translationState,
                translationEnabled = state.showTranslatedPages,
                onClickTranslation = viewModel::toggleTranslatedPages,
                showWebtoonAutoScrollControls = state.viewer is WebtoonViewer,
                isAutoScrollRunning = isAutoScrollRunning,
                autoScrollSpeedTenths = autoScrollSpeedTenths,
                showAutoScrollPanel = showAutoScrollPanel,
                onToggleAutoScroll = {
                    (state.viewer as? WebtoonViewer)?.toggleAutoScroll()
                },
                onToggleAutoScrollPanel = {
                    showAutoScrollPanel = !showAutoScrollPanel
                },
                onSelectAutoScrollPreset = ::setWebtoonAutoScrollSpeed,
                onAutoScrollSpeedChange = ::setWebtoonAutoScrollSpeed,
                onClickSettings = viewModel::openSettingsDialog,
            )

            if (flashOnPageChange) {
                DisplayRefreshHost(
                    hostState = displayRefreshHost,
                )
            }

            val onDismissRequest = viewModel::closeDialog
            when (state.dialog) {
                is ReaderViewModel.Dialog.Loading -> {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(MR.strings.loading))
                            }
                        },
                    )
                }
                is ReaderViewModel.Dialog.Settings -> {
                    ReaderSettingsDialog(
                        onDismissRequest = onDismissRequest,
                        onShowMenus = { setMenuVisibility(true) },
                        onHideMenus = { setMenuVisibility(false) },
                        screenModel = settingsScreenModel,
                    )
                }
                is ReaderViewModel.Dialog.ReadingModeSelect -> {
                    ReadingModeSelectDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        onChange = { stringRes ->
                            menuToggleToast?.cancel()
                            if (!readerPreferences.showReadingMode().get()) {
                                menuToggleToast = toast(stringRes)
                            }
                        },
                    )
                }
                is ReaderViewModel.Dialog.OrientationModeSelect -> {
                    OrientationSelectDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        onChange = { stringRes ->
                            menuToggleToast?.cancel()
                            menuToggleToast = toast(stringRes)
                        },
                    )
                }
                is ReaderViewModel.Dialog.PageActions -> {
                    ReaderPageActionsDialog(
                        onDismissRequest = onDismissRequest,
                        onSetAsCover = viewModel::setAsCover,
                        onShare = viewModel::shareImage,
                        onSave = viewModel::saveImage,
                    )
                }
                null -> {}
            }
        }

        val toolbarColor = ColorUtils.setAlphaComponent(
            SurfaceColors.SURFACE_2.getColor(this),
            if (isNightMode()) 230 else 242, // 90% dark 95% light
        )
        @Suppress("DEPRECATION")
        window.statusBarColor = toolbarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.navigationBarColor = toolbarColor
        }

        // Set initial visibility
        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    /**
     * Sets the visibility of the menu according to [visible].
     */
    private fun setMenuVisibility(visible: Boolean) {
        viewModel.showMenus(visible)
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        } else {
            if (readerPreferences.fullscreen().get()) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer.
     */
    private fun updateViewer() {
        val prevViewer = viewModel.state.value.viewer
        val newViewer = ReadingMode.toViewer(viewModel.getMangaReadingMode(), this)

        if (window.sharedElementEnterTransition is MaterialContainerTransform) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.doOnEnd {
                setOrientation(viewModel.getMangaOrientation())
            }
        } else {
            setOrientation(viewModel.getMangaOrientation())
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            viewerContainer.removeAllViews()
        }
        viewModel.onViewerLoaded(newViewer)
        if (newViewer is WebtoonViewer) {
            newViewer.autoScrollStateChangedListener = { running ->
                isAutoScrollRunning = running
            }
            newViewer.setAutoScrollSpeedTenths(autoScrollSpeedTenths)
            isAutoScrollRunning = newViewer.isAutoScrollRunning()
        } else {
            showAutoScrollPanel = false
            isAutoScrollRunning = false
        }
        updateViewerInset(readerPreferences.fullscreen().get())
        viewerContainer.addView(newViewer.getView())
        updateViewerForVerticalHinge(viewModel.state.value.foldState)

        if (readerPreferences.showReadingMode().get()) {
            showReadingModeToast(viewModel.getMangaReadingMode())
        }

        loadingIndicator = ReaderProgressIndicator(this)
        readerContainer.addView(loadingIndicator)

        startPostponedEnterTransition()
    }

    private fun setWebtoonAutoScrollSpeed(value: Int) {
        val speed = value.coerceIn(
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN,
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX,
        )
        val autoScrollSpeedPref = readerPreferences.webtoonAutoScrollSpeedTenths()
        if (autoScrollSpeedPref.get() != speed) {
            autoScrollSpeedPref.set(speed)
        }
    }

    private fun openMangaScreen() {
        viewModel.manga?.id?.let { id ->
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Constants.SHORTCUT_MANGA
                    putExtra(Constants.MANGA_EXTRA, id)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
    }

    private fun openChapterInWebView() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        viewModel.state.value.assistUrl?.let {
            val intent = WebViewActivity.newIntent(this@ReaderActivity, it, source.id, manga.title)
            startActivity(intent)
        }
    }

    private fun openChapterInBrowser() {
        viewModel.state.value.assistUrl?.let {
            openInBrowser(it.toUri(), forceDefaultBrowser = false)
        }
    }

    private fun shareChapter() {
        viewModel.state.value.assistUrl?.let {
            val intent = it.toUri().toShareIntent(this, type = "text/plain")
            startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
        }
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            readingModeToast?.cancel()
            readingModeToast = toast(ReadingMode.fromPreference(mode).stringRes)
        } catch (e: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    @SuppressLint("RestrictedApi")
    private fun setChapters(viewerChapters: ViewerChapters) {
        readerContainer.removeView(loadingIndicator)
        viewModel.state.value.viewer?.setChapters(viewerChapters)
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the presenter whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    private fun setProgressDialog(show: Boolean) {
        if (show) {
            viewModel.showLoadingDialog()
        } else {
            viewModel.closeDialog()
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(index: Int) {
        val viewer = viewModel.state.value.viewer ?: return
        val currentChapter = viewModel.state.value.currentChapter ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    fun onPageSelected(page: ReaderPage) {
        viewModel.onPageSelected(page)
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage) {
        viewModel.openPageDialog(page)
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        viewModel.preload(chapter)
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        setMenuVisibility(!viewModel.state.value.menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!viewModel.state.value.menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (viewModel.state.value.menuVisible) {
            setMenuVisibility(false)
        }
    }

    fun setNavigationOverlay(navigation: ViewerNavigation, showOnStart: Boolean) {
        navigationOverlay.setNavigation(navigation, showOnStart)
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    private fun onShareImageResult(uri: Uri, page: ReaderPage) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(MR.strings.share_page_info, manga.title, chapter.name, page.number),
        )
        startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
    }

    private fun onCopyImageResult(uri: Uri) {
        val clipboardManager = applicationContext.getSystemService<ClipboardManager>() ?: return
        val clipData = ClipData.newUri(applicationContext.contentResolver, "", uri)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: SaveImageResult) {
        when (result) {
            is SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: SetAsCoverResult) {
        toast(
            when (result) {
                Success -> MR.strings.cover_updated
                AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        if (!honorsOrientationRequests(resources.configuration)) return
        val newOrientation = ReaderOrientation.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Updates viewer inset depending on fullscreen reader preferences.
     */
    private fun updateViewerInset(fullscreen: Boolean) {
        viewModel.state.value.viewer?.getView()?.applyInsetter {
            if (!fullscreen) {
                type(navigationBars = true, statusBars = true) {
                    padding()
                }
            }
        }
    }

    /**
     * Constrains the viewer height for the tabletop upper region.
     *
     * It runs on the fold state that the [ReaderViewModel.State.foldState]
     * observer emits. It reads the status bar inset from the viewer view so
     * the height is measured in content coordinates.
     */
    private fun updateViewerForTabletopPosture(foldState: ReaderFoldState?) {
        val view = viewModel.state.value.viewer?.getView() ?: return
        val statusBarInset = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val tabletopHeight = tabletopViewerHeight(foldState, statusBarInset)
        val params = view.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        params.height = tabletopHeight ?: FrameLayout.LayoutParams.MATCH_PARENT
        view.layoutParams = params
    }

    /**
     * Keeps the viewer clear of an occluding vertical hinge.
     *
     * It runs on the fold state that the [ReaderViewModel.State.foldState]
     * observer emits and after the viewer is replaced. It reads the window
     * width from display metrics so the margins are measured in window
     * pixels.
     */
    private fun updateViewerForVerticalHinge(foldState: ReaderFoldState?) {
        val view = viewModel.state.value.viewer?.getView() ?: return
        val windowWidth = resources.displayMetrics.widthPixels
        val margins = verticalViewerMargins(foldState, windowWidth)
        val params = view.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        params.leftMargin = margins?.left ?: 0
        params.rightMargin = margins?.right ?: 0
        view.layoutParams = params
    }

    /**
     * Class that observes ReaderConfigManager and applies config to Android Window/Views.
     */
    private inner class ReaderConfig {

        /**
         * Initializes the reader config observers.
         */
        init {
            viewModel.readerConfig.backgroundColor
                .onEach { color ->
                    readerContainer.setBackgroundColor(color)
                }
                .launchIn(lifecycleScope)

            viewModel.readerConfig.layerPaint
                .onEach { paint ->
                    viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)
                }
                .launchIn(lifecycleScope)

            viewModel.readerConfig.cutoutShort
                .onEach(::setCutoutShort)
                .launchIn(lifecycleScope)

            viewModel.readerConfig.keepScreenOn
                .onEach(::setKeepScreenOn)
                .launchIn(lifecycleScope)

            viewModel.readerConfig.customBrightnessEnabled
                .flatMapLatest { enabled ->
                    if (enabled) {
                        viewModel.readerConfig.customBrightnessValue.sample(100)
                    } else {
                        flowOf(0)
                    }
                }
                .onEach(::setCustomBrightnessValue)
                .launchIn(lifecycleScope)

            viewModel.readerConfig.fullscreen
                .onEach { fullscreen ->
                    WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
                    updateViewerInset(fullscreen)
                }
                .launchIn(lifecycleScope)
        }

        private fun setCutoutShort(enabled: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

            window.attributes.layoutInDisplayCutoutMode = when (enabled) {
                true -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                false -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            // Trigger relayout
            setMenuVisibility(viewModel.state.value.menuVisible)
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            val readerBrightness = viewModel.readerConfig.calculateReaderBrightness(value)
            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            viewModel.setBrightnessOverlayValue(value)
        }
    }
}

/**
 * Returns true when the device is in tabletop posture.
 *
 * Tabletop posture requires a horizontal, half-open fold that occludes the
 * middle of the window fully.
 */
internal fun isInTabletopPosture(foldState: ReaderFoldState?): Boolean {
    return foldState != null &&
        foldState.orientation == FoldOrientation.Horizontal &&
        foldState.state == FoldState.HalfOpen &&
        foldState.occlusionType == FoldOcclusionType.Full
}

/**
 * Computes the viewer height in pixels for the tabletop upper region.
 *
 * Returns null when the device is not in tabletop posture, so the caller uses
 * MATCH_PARENT. In tabletop posture it returns the height above the fold,
 * reduced by [statusBarInset] because the fold bounds live in window pixels.
 * The result never goes below zero.
 */
internal fun tabletopViewerHeight(
    foldState: ReaderFoldState?,
    statusBarInset: Int,
): Int? {
    if (foldState == null || !isInTabletopPosture(foldState)) return null
    return (foldState.bounds.top - statusBarInset).coerceAtLeast(0)
}

/**
 * Computes the position of the tabletop lower region top in pixels.
 *
 * Returns null when the device is not in tabletop posture, so the caller
 * keeps the controls in their normal top position. In tabletop posture it
 * returns the fold bottom, reduced by [statusBarInset] because the fold
 * bounds live in window pixels. The result never goes below zero. It mirrors
 * [tabletopViewerHeight] with the fold bottom instead of the fold top.
 */
internal fun tabletopControlsTopOffset(
    foldState: ReaderFoldState?,
    statusBarInset: Int,
): Int? {
    if (foldState == null || !isInTabletopPosture(foldState)) return null
    return (foldState.bounds.bottom - statusBarInset).coerceAtLeast(0)
}

/**
 * Computes the height in pixels for the tabletop lower-region controls.
 *
 * Returns null when the device is not in tabletop posture, so the caller
 * leaves the controls at their normal height. In tabletop posture it returns
 * the height below the fold: from [foldState].bounds.bottom to the window
 * bottom, reduced by [bottomInsetPx]. The result never goes below zero. It
 * mirrors [tabletopViewerHeight]'s coordinate-space contract: fold bounds and
 * window height are both in window pixels.
 */
internal fun tabletopControlsMaxHeight(
    foldState: ReaderFoldState?,
    windowHeightPx: Int,
    bottomInsetPx: Int,
): Int? {
    if (foldState == null || !isInTabletopPosture(foldState)) return null
    return (windowHeightPx - foldState.bounds.bottom - bottomInsetPx).coerceAtLeast(0)
}

/**
 * Returns true when the device is in book posture with an occluding hinge.
 *
 * Book posture requires a vertical, half-open fold that occludes the
 * middle of the window fully.
 */
internal fun isInBookPostureWithOccludingHinge(foldState: ReaderFoldState?): Boolean {
    return foldState != null &&
        foldState.orientation == FoldOrientation.Vertical &&
        foldState.state == FoldState.HalfOpen &&
        foldState.occlusionType == FoldOcclusionType.Full
}

/**
 * The horizontal insets in pixels that keep content clear of the hinge.
 *
 * [left] is the usable region to the left of the fold. [right] is the usable
 * region to the right of the fold. The values live in window pixels.
 */
internal data class HingeInsets(
    val left: Int,
    val right: Int,
)

/**
 * Computes the horizontal insets in window pixels for an occluding hinge.
 *
 * Returns null when the device is not in book posture with an occluding
 * hinge, so the caller applies no insets. In book posture it returns the
 * regions on both sides of the fold, extended to the window edges. The
 * results never go below zero.
 */
internal fun verticalHingeInsets(
    foldState: ReaderFoldState?,
    windowWidth: Int,
): HingeInsets? {
    if (foldState == null || !isInBookPostureWithOccludingHinge(foldState)) return null
    val bounds = foldState.bounds
    return HingeInsets(
        left = bounds.left.coerceAtLeast(0),
        right = (windowWidth - bounds.right).coerceAtLeast(0),
    )
}

/**
 * The horizontal margins in pixels that place the viewer on one side of the
 * hinge.
 *
 * The values apply to the viewer's layout params as [ViewerMargins.left] and
 * [ViewerMargins.right].
 */
internal data class ViewerMargins(
    val left: Int,
    val right: Int,
)

/**
 * Computes the viewer margins that keep the page clear of an occluding hinge.
 *
 * Returns null when the device is not in book posture with an occluding
 * hinge, so the caller resets the margins to zero. In book posture it fits
 * the page in the larger region on one side of the fold, left on a tie. The
 * results never go below zero.
 */
internal fun verticalViewerMargins(
    foldState: ReaderFoldState?,
    windowWidth: Int,
): ViewerMargins? {
    if (foldState == null || !isInBookPostureWithOccludingHinge(foldState)) return null
    val bounds = foldState.bounds
    val leftUsable = bounds.left.coerceAtLeast(0)
    val rightUsable = (windowWidth - bounds.right).coerceAtLeast(0)
    return if (leftUsable >= rightUsable) {
        ViewerMargins(
            left = 0,
            right = (windowWidth - bounds.left).coerceAtLeast(0),
        )
    } else {
        ViewerMargins(
            left = bounds.right.coerceAtLeast(0),
            right = 0,
        )
    }
}

/**
 * The horizontal insets in Dp that keep controls clear of the hinge.
 *
 * It is the Dp equivalent of [HingeInsets], converted with the composition
 * density so a composable layout does no pixel math itself.
 */
data class DpHingeInsets(
    val left: Dp,
    val right: Dp,
)

/**
 * The Dp constraints that place the reader controls in the tabletop lower
 * region.
 *
 * [topOffset] is the top of the lower region in the overlay's coordinates.
 * [maxHeight] is the largest height the controls may use. Both are null-when-
 * not-tabletop values converted to Dp.
 */
data class TabletopControlsConstraints(
    val topOffset: Dp,
    val maxHeight: Dp,
)

/**
 * Computes the vertical-hinge insets in Dp for the overlay composables.
 *
 * It reads the window width from the composition configuration and converts
 * the pixel insets with the composition density. Call it inside a composition
 * so it reacts to configuration changes.
 */
@Composable
private fun hingeInsetsDp(foldState: ReaderFoldState?): DpHingeInsets? {
    val density = LocalDensity.current
    val windowWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    return verticalHingeInsets(foldState, windowWidthPx)?.let {
        with(density) {
            DpHingeInsets(
                left = it.left.toDp(),
                right = it.right.toDp(),
            )
        }
    }
}

/**
 * Computes the Dp constraints that place the reader controls in the tabletop
 * lower region.
 *
 * Returns null when the device is not in tabletop posture, so the overlay
 * keeps its normal layout. It reads the status bar and navigation bar insets
 * from the overlay view so the values are measured in the overlay's content
 * coordinates, matching [tabletopControlsMaxHeight]'s coordinate-space
 * contract. Call it inside a composition so it reacts to configuration
 * changes.
 */
@Composable
private fun tabletopControlsConstraintsDp(foldState: ReaderFoldState?): TabletopControlsConstraints? {
    val density = LocalDensity.current
    val view = LocalView.current
    val rootInsets = ViewCompat.getRootWindowInsets(view)
    val statusBarInsetPx = rootInsets
        ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    val navigationBarInsetPx = rootInsets
        ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    val windowHeightPx = WindowMetricsCalculator.getOrCreate()
        .computeCurrentWindowMetrics(LocalContext.current)
        .bounds
        .height()
    return tabletopControlsTopOffset(foldState, statusBarInsetPx)?.let { topOffsetPx ->
        tabletopControlsMaxHeight(foldState, windowHeightPx, navigationBarInsetPx)?.let { maxHeightPx ->
            with(density) {
                TabletopControlsConstraints(
                    topOffset = topOffsetPx.toDp(),
                    maxHeight = maxHeightPx.toDp(),
                )
            }
        }
    }
}
