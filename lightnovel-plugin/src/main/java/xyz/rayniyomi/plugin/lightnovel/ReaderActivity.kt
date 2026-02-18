package xyz.rayniyomi.plugin.lightnovel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import xyz.rayniyomi.plugin.lightnovel.ui.ReaderScreen
import xyz.rayniyomi.plugin.lightnovel.ui.ReaderViewModel

class ReaderActivity : ComponentActivity() {
    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initResult = viewModel.initialize(
            bookId = intent.getStringExtra(EXTRA_BOOK_ID),
            restoredChapter = savedInstanceState?.getInt(ReaderViewModel.KEY_CURRENT_CHAPTER),
            restoredOffset = savedInstanceState?.getInt(ReaderViewModel.KEY_PENDING_OFFSET),
        )
        if (initResult is ReaderViewModel.InitResult.Error) {
            Toast.makeText(this, getString(initResult.messageRes), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            ReaderScreen(
                title = uiState.title,
                chapterIndicator = uiState.chapterIndicator,
                chapterText = uiState.chapterText,
                previousEnabled = uiState.previousEnabled,
                nextEnabled = uiState.nextEnabled,
                restoreOffset = uiState.restoreOffset,
                onPreviousClick = viewModel::onPreviousClick,
                onNextClick = viewModel::onNextClick,
                onPersistOffset = viewModel::onPersistOffset,
            )
        }
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
