package mihon.core.migration

import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder

class MigrationContext(
    val dryrun: Boolean,
    val graph: AppGraph? = AppGraphHolder.graphOrNull,
)
