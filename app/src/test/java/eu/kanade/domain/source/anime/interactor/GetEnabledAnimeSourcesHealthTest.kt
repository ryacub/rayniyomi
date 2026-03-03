package eu.kanade.domain.source.anime.interactor

import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.manga.model.SourceHealth
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.domain.source.manga.repository.SourceHealthRepository

@Execution(ExecutionMode.CONCURRENT)
class GetEnabledAnimeSourcesHealthTest {

    private val repository: AnimeSourceRepository = mockk()
    private val preferences: SourcePreferences = mockk()
    private val healthRepository: SourceHealthRepository = mockk()

    private fun createSource(
        id: Long,
        lang: String = "en",
        name: String = "Source $id",
        isStub: Boolean = false,
    ): AnimeSource = AnimeSource(
        id = id,
        lang = lang,
        name = name,
        supportsLatest = true,
        isStub = isStub,
    )

    private fun <T> mockPref(value: T): Preference<T> {
        val pref: Preference<T> = mockk()
        every { pref.changes() } returns flowOf(value)
        every { pref.get() } returns value
        return pref
    }

    @BeforeEach
    fun setUp() {
        every { preferences.pinnedAnimeSources() } returns mockPref(emptySet())
        every { preferences.enabledLanguages() } returns mockPref(setOf("en"))
        every { preferences.disabledAnimeSources() } returns mockPref(emptySet())
        every { preferences.lastUsedAnimeSource() } returns mockPref(0L)
        every { preferences.showBrokenAnimeSources() } returns mockPref(false)
    }

    @Test
    fun `broken sources hidden when showBroken is false`() = runTest {
        val sources = listOf(
            createSource(1L, name = "Healthy Source"),
            createSource(2L, name = "Broken Source"),
            createSource(3L, name = "Degraded Source"),
        )
        every { repository.getAnimeSources() } returns flowOf(sources)
        every { healthRepository.getAll() } returns flowOf(
            mapOf(
                1L to SourceHealth(1L, SourceHealthStatus.HEALTHY, 0L, 0, null),
                2L to SourceHealth(2L, SourceHealthStatus.BROKEN, 0L, 3, "Error"),
                3L to SourceHealth(3L, SourceHealthStatus.DEGRADED, 0L, 1, "Slow"),
            ),
        )
        every { preferences.showBrokenAnimeSources() } returns mockPref(false)

        val interactor = GetEnabledAnimeSources(repository, preferences, healthRepository)
        val result = interactor.subscribe().first()

        result shouldHaveSize 2
        result.none { it.name == "Broken Source" } shouldBe true
        result.any { it.name == "Healthy Source" } shouldBe true
        result.any { it.name == "Degraded Source" } shouldBe true
    }

    @Test
    fun `broken sources shown when showBroken is true`() = runTest {
        val sources = listOf(
            createSource(1L, name = "Healthy Source"),
            createSource(2L, name = "Broken Source"),
        )
        every { repository.getAnimeSources() } returns flowOf(sources)
        every { healthRepository.getAll() } returns flowOf(
            mapOf(
                1L to SourceHealth(1L, SourceHealthStatus.HEALTHY, 0L, 0, null),
                2L to SourceHealth(2L, SourceHealthStatus.BROKEN, 0L, 3, "Error"),
            ),
        )
        every { preferences.showBrokenAnimeSources() } returns mockPref(true)

        val interactor = GetEnabledAnimeSources(repository, preferences, healthRepository)
        val result = interactor.subscribe().first()

        result shouldHaveSize 2
        result.any { it.name == "Broken Source" } shouldBe true
    }

    @Test
    fun `health status mapped from repository to sources`() = runTest {
        val sources = listOf(
            createSource(1L, name = "Source A"),
            createSource(2L, name = "Source B"),
            createSource(3L, name = "Source C"),
        )
        every { repository.getAnimeSources() } returns flowOf(sources)
        every { healthRepository.getAll() } returns flowOf(
            mapOf(
                1L to SourceHealth(1L, SourceHealthStatus.HEALTHY, 0L, 0, null),
                2L to SourceHealth(2L, SourceHealthStatus.DEGRADED, 0L, 1, "Slow"),
            ),
        )
        every { preferences.showBrokenAnimeSources() } returns mockPref(true)

        val interactor = GetEnabledAnimeSources(repository, preferences, healthRepository)
        val result = interactor.subscribe().first()

        val sourceA = result.first { it.name == "Source A" }
        val sourceB = result.first { it.name == "Source B" }
        val sourceC = result.first { it.name == "Source C" }

        sourceA.healthStatus shouldBe SourceHealthStatus.HEALTHY
        sourceB.healthStatus shouldBe SourceHealthStatus.DEGRADED
        sourceC.healthStatus shouldBe SourceHealthStatus.UNKNOWN
    }

    @Test
    fun `stub sources always get UNKNOWN status regardless of health data`() = runTest {
        val sources = listOf(
            createSource(1L, name = "Stub Source", isStub = true),
        )
        every { repository.getAnimeSources() } returns flowOf(sources)
        every { healthRepository.getAll() } returns flowOf(
            mapOf(
                1L to SourceHealth(1L, SourceHealthStatus.HEALTHY, 0L, 0, null),
            ),
        )
        every { preferences.showBrokenAnimeSources() } returns mockPref(true)

        val interactor = GetEnabledAnimeSources(repository, preferences, healthRepository)
        val result = interactor.subscribe().first()

        result.first().healthStatus shouldBe SourceHealthStatus.UNKNOWN
    }

    @Test
    fun `health filtering reacts to preference changes`() = runTest {
        val sources = listOf(
            createSource(1L, name = "Healthy Source"),
            createSource(2L, name = "Broken Source"),
        )
        every { repository.getAnimeSources() } returns flowOf(sources)
        every { healthRepository.getAll() } returns flowOf(
            mapOf(
                1L to SourceHealth(1L, SourceHealthStatus.HEALTHY, 0L, 0, null),
                2L to SourceHealth(2L, SourceHealthStatus.BROKEN, 0L, 3, "Error"),
            ),
        )

        val showBrokenFlow = MutableStateFlow(false)
        val showBrokenPref: Preference<Boolean> = mockk()
        every { showBrokenPref.changes() } returns showBrokenFlow
        every { showBrokenPref.get() } returns false
        every { preferences.showBrokenAnimeSources() } returns showBrokenPref

        val interactor = GetEnabledAnimeSources(repository, preferences, healthRepository)
        val flow = interactor.subscribe()

        // Initially hidden
        val firstResult = flow.first()
        firstResult shouldHaveSize 1
        firstResult.first().name shouldBe "Healthy Source"

        // Toggle showBroken → true, broken source should now appear
        showBrokenFlow.value = true
        val secondResult = flow.first()
        secondResult shouldHaveSize 2
        secondResult.any { it.name == "Broken Source" } shouldBe true

        // Toggle back → false, broken source hidden again
        showBrokenFlow.value = false
        val thirdResult = flow.first()
        thirdResult shouldHaveSize 1
        thirdResult.none { it.name == "Broken Source" } shouldBe true
    }
}
