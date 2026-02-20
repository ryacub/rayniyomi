package eu.kanade.presentation.more.settings.screen.browse

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mihon.domain.extensionrepo.model.ExtensionRepo
import org.junit.jupiter.api.AfterEach
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
    fun `initial state is Loading`() = runTest {
        // Keep the state deterministic by preventing any repo emission during init.
        val deps = createMockDependencies(emptyFlow())
        val model = ExtensionReposScreenModel(deps)

        // Model starts in Loading state before any repository list is emitted.
        assertInstanceOf(RepoScreenState.Loading::class.java, model.state.value)
    }

    @Test
    fun `dependencies interface is called correctly`() = runTest {
        val deps = createMockDependencies(flowOf(emptyList()))
        val model = ExtensionReposScreenModel(deps)

        // Verify subscribeAll is called during init
        coVerify { deps.subscribeAll() }
    }

    private fun createMockDependencies(
        reposFlow: kotlinx.coroutines.flow.Flow<List<ExtensionRepo>>,
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
