package tachiyomi.domain.recovery.model

enum class RecoveryState {
    SELECTED,
    SEARCHING,
    NEEDS_REVIEW,
    MISSING,
    READY,
    APPLYING,
    INTERRUPTED,
    COMPLETED,
    FAILED,
    SKIPPED,
    UNSUPPORTED,
    ;

    companion object {
        val finished: Set<RecoveryState> get() = setOf(COMPLETED, SKIPPED, UNSUPPORTED)
        val running: Set<RecoveryState> get() = setOf(SEARCHING, APPLYING)
    }
}
