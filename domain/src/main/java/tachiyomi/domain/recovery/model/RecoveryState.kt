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
        val searchable: Set<RecoveryState> get() = setOf(SELECTED, MISSING, FAILED, INTERRUPTED)
        val applicable: Set<RecoveryState> get() = setOf(READY, FAILED, INTERRUPTED)
        val locked: Set<RecoveryState> get() = running + setOf(COMPLETED, UNSUPPORTED)
    }
}
