package xyz.rayniyomi.plugin.lightnovel

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.rayniyomi.plugin.lightnovel.data.ImportTooLargeException
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import xyz.rayniyomi.plugin.lightnovel.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: NovelStorage
    private lateinit var listAdapter: ArrayAdapter<String>

    private val books = mutableListOf<NovelBook>()

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
                Toast.makeText(
                    this@MainActivity,
                    getString(errorMessageRes),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            refreshBooks()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = NovelStorage(this)

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        binding.booksList.adapter = listAdapter
        binding.booksList.emptyView = binding.emptyText

        binding.importEpubButton.setOnClickListener {
            importLauncher.launch(arrayOf("application/epub+zip", "application/octet-stream"))
        }

        binding.booksList.setOnItemClickListener { _, _, position, _ ->
            val book = books.getOrNull(position) ?: return@setOnItemClickListener
            val intent = Intent(this, ReaderActivity::class.java)
                .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBooks()
    }

    private fun refreshBooks() {
        books.clear()
        books.addAll(storage.listBooks())

        listAdapter.clear()
        listAdapter.addAll(books.map { it.title })
        listAdapter.notifyDataSetChanged()
    }
}
