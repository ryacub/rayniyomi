package eu.kanade.tachiyomi.extension

enum class InstallStep {
    Idle,
    Pending,
    Downloading,
    Installing,
    Installed,
    Error,
    ;

    fun isCompleted(): Boolean {
        return this == Installed || this == Error || this == Idle
    }
}

internal fun Map<String, InstallStep>.withInstallStep(
    pkgName: String,
    installStep: InstallStep,
): Map<String, InstallStep> = this + (pkgName to installStep)

internal fun Map<String, InstallStep>.completeInstall(pkgName: String): Map<String, InstallStep> =
    if (this[pkgName] == InstallStep.Error) this else this - pkgName

internal fun Map<String, InstallStep>.dismissInstallError(pkgName: String): Map<String, InstallStep> =
    if (this[pkgName] == InstallStep.Error) this - pkgName else this
