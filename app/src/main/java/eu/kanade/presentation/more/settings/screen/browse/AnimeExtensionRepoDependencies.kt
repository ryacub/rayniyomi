package eu.kanade.presentation.more.settings.screen.browse

import kotlinx.coroutines.flow.Flow
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo

/**
 * Adapter that bridges Anime-specific interactors to the generic Dependencies interface.
 */
class AnimeExtensionRepoDependencies(
    private val getExtensionRepo: GetAnimeExtensionRepo,
    private val createExtensionRepo: CreateAnimeExtensionRepo,
    private val deleteExtensionRepo: DeleteAnimeExtensionRepo,
    private val replaceExtensionRepo: ReplaceAnimeExtensionRepo,
    private val updateExtensionRepo: UpdateAnimeExtensionRepo,
) : ExtensionReposScreenModel.Dependencies {

    override fun subscribeAll(): Flow<List<ExtensionRepo>> = getExtensionRepo.subscribeAll()

    override suspend fun createRepo(baseUrl: String): ExtensionReposScreenModel.CreateResult {
        return when (val result = createExtensionRepo.await(baseUrl)) {
            CreateAnimeExtensionRepo.Result.InvalidUrl ->
                ExtensionReposScreenModel.CreateResult.InvalidUrl
            CreateAnimeExtensionRepo.Result.RepoAlreadyExists ->
                ExtensionReposScreenModel.CreateResult.RepoAlreadyExists
            is CreateAnimeExtensionRepo.Result.DuplicateFingerprint -> {
                ExtensionReposScreenModel.CreateResult.DuplicateFingerprint(result.oldRepo, result.newRepo)
            }
            CreateAnimeExtensionRepo.Result.Success -> ExtensionReposScreenModel.CreateResult.Success
            CreateAnimeExtensionRepo.Result.Error -> ExtensionReposScreenModel.CreateResult.Error
        }
    }

    override suspend fun replaceRepo(newRepo: ExtensionRepo) {
        replaceExtensionRepo.await(newRepo)
    }

    override suspend fun updateAll() {
        updateExtensionRepo.awaitAll()
    }

    override suspend fun deleteRepo(baseUrl: String) {
        deleteExtensionRepo.await(baseUrl)
    }
}
