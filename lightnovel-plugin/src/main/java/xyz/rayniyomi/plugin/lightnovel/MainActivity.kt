package xyz.rayniyomi.plugin.lightnovel

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.rayniyomi.lightnovel.contract.LightNovelTransferDisplayStatus
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.databinding.ActivityMainBinding
import xyz.rayniyomi.plugin.lightnovel.ui.displayReasonText

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var listAdapter: ArrayAdapter<String>

    private val books = mutableListOf<NovelBook>()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult

        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        if (!viewModel.importEpub(uri)) {
            Toast.makeText(this, getString(R.string.import_already_running), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        binding.booksList.adapter = listAdapter
        binding.booksList.emptyView = binding.emptyText

        binding.importEpubButton.setOnClickListener {
            if (viewModel.isImportRunning()) {
                Toast.makeText(this, getString(R.string.import_already_running), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            importLauncher.launch(arrayOf("application/epub+zip", "application/octet-stream"))
        }
        binding.retryImportButton.setOnClickListener {
            if (!viewModel.retryLastImport()) {
                Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            }
        }

        binding.booksList.setOnItemClickListener { _, _, position, _ ->
            val book = books.getOrNull(position) ?: return@setOnItemClickListener
            val intent = Intent(this, ReaderActivity::class.java)
                .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
            startActivity(intent)
        }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBooks()
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.books.collectLatest {
                books.clear()
                books.addAll(it)

                listAdapter.clear()
                listAdapter.addAll(books.map { book -> book.title })
                listAdapter.notifyDataSetChanged()
            }
        }
        lifecycleScope.launch {
            viewModel.importStatus.collectLatest { status ->
                val statusText = status.displayReasonText(this@MainActivity)
                val inProgress = status.displayStatus in setOf(
                    LightNovelTransferDisplayStatus.PREPARING,
                    LightNovelTransferDisplayStatus.IMPORTING,
                    LightNovelTransferDisplayStatus.STALLED,
                    LightNovelTransferDisplayStatus.VERIFYING,
                )
                binding.importStatusText.isVisible = status.displayStatus != LightNovelTransferDisplayStatus.COMPLETED
                binding.importStatusText.text = statusText
                binding.importStatusText.contentDescription = statusText

                binding.importProgressBar.isVisible = inProgress
                if (status.progressPercent == null || status.displayStatus == LightNovelTransferDisplayStatus.STALLED) {
                    binding.importProgressBar.isIndeterminate = true
                } else {
                    binding.importProgressBar.isIndeterminate = false
                    binding.importProgressBar.progress = status.progressPercent
                }
                binding.importProgressBar.contentDescription = statusText
                binding.importEpubButton.isEnabled = !viewModel.isImportRunning()
                binding.retryImportButton.isVisible = status.displayStatus in setOf(
                    LightNovelTransferDisplayStatus.FAILED,
                    LightNovelTransferDisplayStatus.PAUSED_LOW_STORAGE,
                )
            }
        }
    }
}
