package xyz.rayniyomi.plugin.lightnovel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import xyz.rayniyomi.plugin.lightnovel.ui.LightNovelPluginTheme
import xyz.rayniyomi.plugin.lightnovel.ui.ReaderScreen
import xyz.rayniyomi.plugin.lightnovel.ui.ReaderViewModel

class ReaderActivity : ComponentActivity() {
    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fatalInitErrors.collect { messageRes ->
                    Toast.makeText(this@ReaderActivity, getString(messageRes), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        setContent {
            LightNovelPluginTheme {
                val uiState by viewModel.uiState.collectAsState()
                ReaderScreen(
                    title = uiState.title,
                    chapterIndicator = uiState.chapterIndicator,
                    chapterText = uiState.chapterText,
                    previousEnabled = uiState.previousEnabled,
                    nextEnabled = uiState.nextEnabled,
                    restoreOffset = uiState.restoreOffset,
                    isLoading = uiState.isLoading,
                    onPreviousClick = viewModel::onPreviousClick,
                    onNextClick = viewModel::onNextClick,
                    onPersistOffset = viewModel::onPersistOffset,
                )
            }
        }

        viewModel.initialize(
            bookId = intent.getStringExtra(EXTRA_BOOK_ID),
            restoredChapter = savedInstanceState?.getInt(ReaderViewModel.KEY_CURRENT_CHAPTER),
            restoredOffset = savedInstanceState?.getInt(ReaderViewModel.KEY_PENDING_OFFSET),
        )
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.saveInstanceState(outState)
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
    }
}
