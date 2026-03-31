package tachiyomi.macrobenchmark.parity

internal enum class ReaderParityScenario {
    STARTUP_TO_READER_ENTRY,
    OPEN_CHAPTER_LATENCY,
    WEBTOON_JANK_SCROLL,
    LONG_STRIP_MEMORY,
    PREFETCH_BOUNDARY_TRANSITION,
    PROCESS_DEATH_RECOVERY,
    BACKGROUND_FOREGROUND_RESUME,
    OFFLINE_RETRY_AFTER_RESTORE,
    LONG_IMAGES_MIXED_DIMENSIONS,
}

internal data class ReaderParityThresholds(
    val startupAndOpenChapterDeltaPercent: Double,
    val webtoonJankDeltaPercent: Double,
    val longStripMemoryDeltaPercent: Double,
    val minimumRecoveryPassRatePercent: Double,
)

internal data class ReaderParityResult(
    val status: String,
    val phase: String,
    val regressions: List<String>,
    val errors: List<String>,
)
