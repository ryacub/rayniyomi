package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.backupCustomButtonsMapper
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons

class CustomButtonBackupCreator(
    private val getCustomButtons: GetCustomButtons = appGraph.getCustomButtons,
) {
    suspend operator fun invoke(): List<BackupCustomButtons> {
        return getCustomButtons.getAll()
            .map(backupCustomButtonsMapper)
    }
}
