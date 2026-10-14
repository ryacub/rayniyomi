package mihon.domain.extensionrepo.anime.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CreateAnimeExtensionRepoTest {

    @Test
    fun `repository file URLs fetch and save the base repository while invalid URLs do not`() = runTest {
        val baseUrl = "https://example.org/anime"
        val repo = ExtensionRepo(baseUrl, "Anime", "anime", "https://example.org", "fingerprint")
        val repository = mockk<AnimeExtensionRepoRepository>(relaxed = true)
        val service = mockk<ExtensionRepoService>()
        coEvery { service.fetchRepoDetails(baseUrl) } returns repo
        val interactor = CreateAnimeExtensionRepo(repository, service)

        for (file in listOf("repo.json", "index.pb", "index.min.json")) {
            assertEquals(CreateAnimeExtensionRepo.Result.Success, interactor.await("$baseUrl/$file"), file)
        }

        for (url in listOf("http://example.org/anime/repo.json", "$baseUrl/other.json", "$baseUrl/repo.json?query=1")) {
            assertEquals(CreateAnimeExtensionRepo.Result.InvalidUrl, interactor.await(url), url)
        }

        coVerify(exactly = 3) { service.fetchRepoDetails(baseUrl) }
        coVerify(exactly = 3) {
            repository.insertRepo(baseUrl, repo.name, repo.shortName, repo.website, repo.signingKeyFingerprint)
        }
        coVerify(exactly = 3) { service.fetchRepoDetails(any()) }
        coVerify(exactly = 3) { repository.insertRepo(any(), any(), any(), any(), any()) }
    }
}
