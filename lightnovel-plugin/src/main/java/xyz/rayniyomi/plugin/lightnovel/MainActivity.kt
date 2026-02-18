package xyz.rayniyomi.plugin.lightnovel

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toastMessages.collect { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            MainScreen(
                books = uiState.books,
                statusMessage = uiState.statusMessage,
                isLoading = uiState.isLoading,
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
