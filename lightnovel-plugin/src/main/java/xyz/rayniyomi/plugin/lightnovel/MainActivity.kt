package xyz.rayniyomi.plugin.lightnovel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import xyz.rayniyomi.plugin.lightnovel.ui.LightNovelPluginTheme
import xyz.rayniyomi.plugin.lightnovel.ui.MainScreen
import xyz.rayniyomi.plugin.lightnovel.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImportResult(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.restoreStatusMessage(savedInstanceState?.getString(KEY_STATUS_MESSAGE).orEmpty())

        setContent {
            LightNovelPluginTheme {
                val uiState by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.toastMessages.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                MainScreen(
                    books = uiState.books,
                    statusMessage = uiState.statusMessage,
                    isLoading = uiState.isLoading,
                    snackbarHostState = snackbarHostState,
                    onImportClick = {
                        importLauncher.launch(arrayOf("application/epub+zip", "application/octet-stream"))
                    },
                    onBookClick = { book ->
                        val intent = Intent(this, ReaderActivity::class.java)
                            .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
                        startActivity(intent)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBooks()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_STATUS_MESSAGE, viewModel.uiState.value.statusMessage)
    }

    private companion object {
        const val KEY_STATUS_MESSAGE = "key_status_message"
    }
}
