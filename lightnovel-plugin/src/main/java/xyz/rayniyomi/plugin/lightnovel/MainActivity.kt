package xyz.rayniyomi.plugin.lightnovel

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.rayniyomi.plugin.lightnovel.data.ImportTooLargeException
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import xyz.rayniyomi.plugin.lightnovel.ui.MainScreen

class MainActivity : ComponentActivity() {
    private lateinit var storage: NovelStorage

    private var books: List<NovelBook> by mutableStateOf(emptyList())
    private var statusMessage: String? by mutableStateOf(null)

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { storage.importEpub(uri) }
            }

            if (result.isFailure) {
                val errorMessageRes = when (result.exceptionOrNull()) {
                    is ImportTooLargeException -> R.string.import_too_large
                    else -> R.string.import_failed
                }
                val message = getString(errorMessageRes)
                statusMessage = message
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            } else {
                statusMessage = null
            }
            refreshBooks()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = NovelStorage(this)

        setContent {
            MainScreen(
                books = books,
                statusMessage = statusMessage,
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
        refreshBooks()
    }

    private fun refreshBooks() {
        books = storage.listBooks()
    }
}
