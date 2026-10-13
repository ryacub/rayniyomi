package xyz.rayniyomi.cloudstream.data

data class PluginItem(
    val internalName: String,
    val name: String,
    val repositoryUrl: String,
    val available: PluginEntry?,
    val installed: InstalledPlugin?,
) {
    val updateAvailable: Boolean
        get() = installed != null && available != null && installed.hasUpdate(available)
}

data class PluginRows(val items: List<PluginItem>, val hiddenCount: Int) {
    val updateCount: Int
        get() = items.count { it.updateAvailable }
}

fun buildPluginRows(state: PluginState, available: List<AvailablePlugins>): PluginRows {
    val (anime, hidden) = available
        .flatMap { group -> group.entries.map { group.repository to it } }
        .partition { (_, entry) -> entry.isAnimeCompatible }
    val installed = state.installed.associateBy { it.internalName }
    val listed = anime.distinctBy { (_, entry) -> entry.internalName }.map { (repository, entry) ->
        PluginItem(entry.internalName, entry.name, repository.url, entry, installed[entry.internalName])
    }
    val listedNames = listed.map { it.internalName }.toSet()
    val unlisted = state.installed.filter { it.internalName !in listedNames }.map {
        PluginItem(it.internalName, it.name, it.repositoryUrl, available = null, installed = it)
    }
    return PluginRows((listed + unlisted).sortedBy { it.name.lowercase() }, hidden.size)
}
