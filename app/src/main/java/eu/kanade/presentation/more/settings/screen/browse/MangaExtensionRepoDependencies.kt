package eu.kanade.presentation.more.settings.screen.browse

import kotlinx.coroutines.flow.Flow
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo

/**
 * Adapter that bridges Manga-specific interactors to the generic Dependencies interface.
 */
class MangaExtensionRepoDependencies(
    private val getExtensionRepo: GetMangaExtensionRepo,
    private val createExtensionRepo: CreateMangaExtensionRepo,
    private val deleteExtensionRepo: DeleteMangaExtensionRepo,
    private val replaceExtensionRepo: ReplaceMangaExtensionRepo,
    private val updateExtensionRepo: UpdateMangaExtensionRepo,
) : ExtensionReposScreenModel.Dependencies {

    override fun subscribeAll(): Flow<List<ExtensionRepo>> = getExtensionRepo.subscribeAll()

    override suspend fun createRepo(baseUrl: String): ExtensionReposScreenModel.CreateResult {
        return when (val result = createExtensionRepo.await(baseUrl)) {
            CreateMangaExtensionRepo.Result.InvalidUrl ->
                ExtensionReposScreenModel.CreateResult.InvalidUrl
            CreateMangaExtensionRepo.Result.RepoAlreadyExists ->
                ExtensionReposScreenModel.CreateResult.RepoAlreadyExists
            is CreateMangaExtensionRepo.Result.DuplicateFingerprint -> {
                ExtensionReposScreenModel.CreateResult.DuplicateFingerprint(result.oldRepo, result.newRepo)
            }
            CreateMangaExtensionRepo.Result.Success -> ExtensionReposScreenModel.CreateResult.Success
            CreateMangaExtensionRepo.Result.Error -> ExtensionReposScreenModel.CreateResult.Error
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
