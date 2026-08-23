package eu.kanade.tachiyomi.extension.anime

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class AnimeExtensionManagerInitTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `install receiver registration is deferred off the constructing thread`() = runTest {
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val manager = createManager(events, dispatcher)

        verify(exactly = 0) { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) }
        advanceUntilIdle()
        verify(exactly = 1) { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) }
        manager.isInitialized.value shouldBe true
    }

    @Test
    fun `registration precedes the initial extension scan`() = runTest {
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        createManager(events, dispatcher)
        advanceUntilIdle()

        events shouldBe listOf("registered", "scan-started")
        coVerify(exactly = 1) { AnimeExtensionLoader.loadExtensions(any()) }
    }

    @Test
    fun `initial scan still runs when receiver registration fails`() = runTest {
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) } throws
            RuntimeException("registration failed")
        mockkObject(AnimeExtensionLoader)
        coEvery { AnimeExtensionLoader.loadExtensions(any()) } answers {
            events += "scan-started"
            emptyList()
        }

        val manager = AnimeExtensionManager(
            context = mockk(relaxed = true),
            preferences = SourcePreferences(MutablePreferenceStore()),
            trustExtension = mockk(relaxed = true),
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { AnimeExtensionLoader.loadExtensions(any()) }
        manager.isInitialized.value shouldBe true
    }

    private fun createManager(
        events: MutableList<String>,
        ioDispatcher: CoroutineDispatcher,
    ): AnimeExtensionManager {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) } answers {
            events += "registered"
            mockk(relaxed = true)
        }
        mockkObject(AnimeExtensionLoader)
        coEvery { AnimeExtensionLoader.loadExtensions(any()) } answers {
            events += "scan-started"
            emptyList()
        }

        return AnimeExtensionManager(
            context = mockk(relaxed = true),
            preferences = SourcePreferences(MutablePreferenceStore()),
            trustExtension = mockk(relaxed = true),
            ioDispatcher = ioDispatcher,
        )
    }

    private class MutablePreferenceStore(initialValues: Map<String, Any?> = emptyMap()) : PreferenceStore {
        private val data = initialValues.toMutableMap()

        override fun getString(key: String, defaultValue: String): Preference<String> = MutablePreference(
            key,
            defaultValue,
        )

        override fun getLong(key: String, defaultValue: Long): Preference<Long> = MutablePreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> = MutablePreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = MutablePreference(
            key,
            defaultValue,
        )

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = MutablePreference(
            key,
            defaultValue,
        )

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            MutablePreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = MutablePreference(key, defaultValue)

        override fun getAll(): Map<String, *> = data

        private inner class MutablePreference<T>(
            private val key: String,
            private val defaultValue: T,
        ) : Preference<T> {
            private val state = MutableStateFlow(get())

            override fun key(): String = key

            override fun get(): T {
                @Suppress("UNCHECKED_CAST")
                return (data[key] as T?) ?: defaultValue
            }

            override fun set(value: T) {
                data[key] = value
                state.value = value
            }

            override fun isSet(): Boolean = data.containsKey(key)

            override fun delete() {
                data.remove(key)
                state.value = defaultValue
            }

            override fun defaultValue(): T = defaultValue

            override fun changes(): Flow<T> = state.asStateFlow()

            override fun stateIn(scope: CoroutineScope): StateFlow<T> = state.asStateFlow()
        }
    }
}
