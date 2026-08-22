package eu.kanade.tachiyomi.security

import android.content.SharedPreferences
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TranslationCredentialAccessTest {

    private lateinit var data: ConcurrentHashMap<String, String>
    private lateinit var plainPrefs: SharedPreferences
    private lateinit var storage: BlockingSecureStorage
    private lateinit var translationPreferences: TranslationPreferences

    @BeforeEach
    fun setup() {
        data = ConcurrentHashMap(
            mapOf(
                "translation_provider" to "CLAUDE",
                "translation_model" to "legacy-model",
            ),
        )
        plainPrefs = statefulSharedPreferences(data)
        storage = BlockingSecureStorage()
        RayniyomiSecurePrefs.initForTesting(storage)
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"

        val delegate = mockk<PreferenceStore>()
        every { delegate.getString(any(), any()) } answers {
            MapPreference(firstArg(), secondArg(), data)
        }
        translationPreferences = TranslationPreferences(SecurePreferenceStore(delegate))
    }

    @Test
    fun `user key and model writes wait for migration and win`() {
        storage.blockProviderKeyRead()
        val migrationThread = thread {
            TranslationApiKeyMigration.migrate(plainPrefs, providerAtStartup = "CLAUDE")
        }

        storage.providerKeyRead.await(5, TimeUnit.SECONDS) shouldBe true
        val userThread = thread {
            translationPreferences.translationApiKey(TranslationProvider.CLAUDE).set("user-key")
            translationPreferences.translationModel(TranslationProvider.CLAUDE).set("user-model")
        }

        waitForBlocked(userThread)
        storage.releaseProviderKeyRead.countDown()
        migrationThread.join(5_000)
        userThread.join(5_000)

        migrationThread.isAlive shouldBe false
        userThread.isAlive shouldBe false
        translationPreferences.translationApiKey(TranslationProvider.CLAUDE).get() shouldBe "user-key"
        translationPreferences.translationModel(TranslationProvider.CLAUDE).get() shouldBe "user-model"
    }

    @Test
    fun `model write waits for migration`() {
        storage.blockProviderKeyRead()
        val migrationThread = thread {
            TranslationApiKeyMigration.migrate(plainPrefs, providerAtStartup = "CLAUDE")
        }

        storage.providerKeyRead.await(5, TimeUnit.SECONDS) shouldBe true
        val userThread = thread {
            translationPreferences.translationModel(TranslationProvider.CLAUDE).set("user-model")
        }

        waitForBlocked(userThread)
        storage.releaseProviderKeyRead.countDown()
        migrationThread.join(5_000)
        userThread.join(5_000)

        userThread.isAlive shouldBe false
        translationPreferences.translationModel(TranslationProvider.CLAUDE).get() shouldBe "user-model"
    }

    @Test
    fun `reactive read waits for migration and returns the migrated key`() {
        val apiKey = translationPreferences.translationApiKey(TranslationProvider.CLAUDE)
        storage.blockProviderKeyRead()
        val migrationThread = thread {
            TranslationApiKeyMigration.migrate(plainPrefs, providerAtStartup = "CLAUDE")
        }

        storage.providerKeyRead.await(5, TimeUnit.SECONDS) shouldBe true
        var observedKey: String? = null
        val collectorThread = thread {
            observedKey = runBlocking { apiKey.changes().first() }
        }

        waitForBlocked(collectorThread)
        storage.releaseProviderKeyRead.countDown()
        migrationThread.join(5_000)
        collectorThread.join(5_000)

        collectorThread.isAlive shouldBe false
        observedKey shouldBe "legacy-key"
    }

    private fun waitForBlocked(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (thread.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.yield()
        }
        thread.state shouldBe Thread.State.BLOCKED
    }

    private class BlockingSecureStorage : SecureStorage {
        private val data = ConcurrentHashMap<String, String>()
        private val blockNextProviderRead = AtomicBoolean(false)
        val providerKeyRead = CountDownLatch(1)
        val releaseProviderKeyRead = CountDownLatch(1)

        fun blockProviderKeyRead() {
            blockNextProviderRead.set(true)
        }

        override fun getString(key: String): String? {
            if (key == "translation_api_key_claude" && blockNextProviderRead.compareAndSet(true, false)) {
                providerKeyRead.countDown()
                releaseProviderKeyRead.await(5, TimeUnit.SECONDS) shouldBe true
            }
            return data[key]
        }

        override fun putString(key: String, value: String?) {
            if (value == null) data.remove(key) else data[key] = value
        }
    }

    private class MapPreference(
        private val key: String,
        private val defaultValue: String,
        private val data: ConcurrentHashMap<String, String>,
    ) : Preference<String> {
        private val state = MutableStateFlow(get())

        override fun key(): String = key
        override fun get(): String = data[key] ?: defaultValue
        override fun set(value: String) {
            data[key] = value
            state.value = value
        }
        override fun isSet(): Boolean = data.containsKey(key)
        override fun delete() {
            data.remove(key)
            state.value = defaultValue
        }
        override fun defaultValue(): String = defaultValue
        override fun changes(): Flow<String> = state
        override fun stateIn(scope: CoroutineScope): StateFlow<String> = state
    }

    private fun statefulSharedPreferences(data: ConcurrentHashMap<String, String>): SharedPreferences {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(any(), any()) } answers { data[firstArg()] ?: secondArg() }
        every { prefs.contains(any()) } answers { data.containsKey(firstArg()) }
        every { prefs.edit() } answers { statefulEditor(data) }
        return prefs
    }

    private fun statefulEditor(data: ConcurrentHashMap<String, String>): SharedPreferences.Editor {
        val editor = mockk<SharedPreferences.Editor>()
        val writes = mutableMapOf<String, String>()
        val removals = mutableSetOf<String>()
        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String?>()
            if (value == null) removals += key else writes[key] = value
            editor
        }
        every { editor.remove(any()) } answers {
            removals += firstArg<String>()
            editor
        }
        every { editor.commit() } answers {
            removals.forEach(data::remove)
            data.putAll(writes)
            true
        }
        every { editor.apply() } answers {
            removals.forEach(data::remove)
            data.putAll(writes)
        }
        return editor
    }
}
