package eu.kanade.presentation.more.settings.screen.browse

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mihon.domain.extensionrepo.model.ExtensionRepo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for ExtensionReposScreenModel.
 *
 * Note: Many tests are limited because the model extends Voyager's StateScreenModel,
 * which requires screenModelScope. Tests are focused on constructor and static behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionReposScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() {
        // Keep the state deterministic by preventing any repo emission during init.
        val deps = createMockDependencies(emptyFlow())
        val model = ExtensionReposScreenModel(deps)

        // Model starts in Loading state before any repository list is emitted.
        assertInstanceOf(RepoScreenState.Loading::class.java, model.state.value)
    }

    @Test
    fun `dependencies interface is called correctly`() {
        val deps = createMockDependencies(flowOf(emptyList()))
        val model = ExtensionReposScreenModel(deps)

        // subscribeAll() runs on Dispatchers.IO through launchIO, outside every test
        // scheduler. The state transition is the completion signal: first {} resumes on
        // that event alone, so no polling and no fixed wait participate in the ordering.
        // runBlocking keeps withTimeout on the real clock, where it only caps a broken
        // run; it never gates the happy path.
        runBlocking {
            withTimeout(5_000) {
                model.state.first { it !is RepoScreenState.Loading }
            }
        }

        coVerify { deps.subscribeAll() }
    }

    @Test
    fun `invalid url event is delivered even when collector attaches late`() {
        val deps = createMockDependencies(emptyFlow())
        coEvery { deps.createRepo("not-a-url") } returns ExtensionReposScreenModel.CreateResult.InvalidUrl
        val model = ExtensionReposScreenModel(deps)

        model.createRepo("not-a-url")

        // The producer runs on Dispatchers.IO through launchIO. The buffered channel
        // hands the event to this late collector as an event wait, not a timed one.
        val event = runBlocking {
            withTimeout(5_000) {
                model.events.first()
            }
        }

        assertEquals(RepoEvent.InvalidUrl, event)
        coVerify { deps.createRepo("not-a-url") }
    }

    private fun createMockDependencies(
        reposFlow: Flow<List<ExtensionRepo>>,
    ): ExtensionReposScreenModel.Dependencies {
        return mockk {
            every { subscribeAll() } returns reposFlow
            coEvery { createRepo(any()) } returns ExtensionReposScreenModel.CreateResult.Success
            coEvery { deleteRepo(any()) } returns Unit
            coEvery { replaceRepo(any()) } returns Unit
            coEvery { updateAll() } returns Unit
        }
    }

    private fun createTestRepo(baseUrl: String): ExtensionRepo {
        return ExtensionRepo(
            baseUrl = baseUrl,
            name = "Test Repo",
            shortName = "TR",
            website = "https://example.com",
            signingKeyFingerprint = "ABC123",
        )
    }
}
