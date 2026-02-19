package xyz.rayniyomi.plugin.lightnovel.data

/**
 * Represents the result of a storage integrity check performed by [NovelStorage.checkIntegrity].
 *
 * Consumers should react to [Corrupted] by offering a recovery UI and calling
 * [NovelStorage.clearAndRecover] rather than propagating exceptions to the host.
 */
sealed interface NovelStorageState {

    /** The library file is absent or can be deserialised without errors. */
    data object Ok : NovelStorageState

    /**
     * The library file exists but could not be parsed.
     *
     * @property reason A human-readable description of the parse failure, suitable for logging.
     *   This value is never shown directly in UI; use a localised string resource instead.
     */
    data class Corrupted(val reason: String) : NovelStorageState
}
