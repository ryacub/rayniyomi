package xyz.rayniyomi.plugin.lightnovel.data

/**
 * Schema migration utilities for [NovelLibraryEnvelope].
 *
 * Each named migration step upgrades the library from version N to N+1.
 * [migrateToLatest] applies all necessary steps sequentially until the data
 * reaches [LATEST_SCHEMA_VERSION].
 *
 * ### Adding a new migration
 * 1. Increment [LATEST_SCHEMA_VERSION].
 * 2. Add a private `migrateVxToVy` function implementing the transformation.
 * 3. Add a new `else -> migrateVxToVy(envelope)` branch in [migrateToLatest].
 * 4. Add unit tests in `NovelDataMigrationTest` covering the new step.
 */
object NovelSchemaMigrations {

    /** The current on-disk schema version produced by this build. */
    const val LATEST_SCHEMA_VERSION: Int = 2

    /**
     * Upgrades [envelope] from its current [NovelLibraryEnvelope.schemaVersion] to
     * [LATEST_SCHEMA_VERSION] by running all intermediate migration steps in order.
     *
     * If the envelope is already at the latest version it is returned unchanged.
     */
    fun migrateToLatest(envelope: NovelLibraryEnvelope): NovelLibraryEnvelope {
        if (envelope.schemaVersion > LATEST_SCHEMA_VERSION) {
            error(
                "Plugin data schema version ${envelope.schemaVersion} is newer than supported " +
                    "version $LATEST_SCHEMA_VERSION. Downgrade detected.",
            )
        }
        var current = envelope
        while (current.schemaVersion < LATEST_SCHEMA_VERSION) {
            current = when (current.schemaVersion) {
                1 -> migrateV1ToV2(current)
                else -> error(
                    "Unknown schema version ${current.schemaVersion}. " +
                        "Cannot migrate to $LATEST_SCHEMA_VERSION.",
                )
            }
        }
        return current
    }

    /**
     * v1 → v2: Introduce [NovelBook.readingTimeMinutes].
     *
     * All existing books receive a default of 0 minutes because v1 did not track reading time.
     */
    private fun migrateV1ToV2(envelope: NovelLibraryEnvelope): NovelLibraryEnvelope {
        val migratedBooks = envelope.library.books.map { book ->
            book.copy(readingTimeMinutes = 0L)
        }
        return envelope.copy(
            schemaVersion = 2,
            library = envelope.library.copy(books = migratedBooks),
        )
    }
}
