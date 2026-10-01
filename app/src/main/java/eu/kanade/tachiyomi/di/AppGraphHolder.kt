package eu.kanade.tachiyomi.di

object AppGraphHolder {
    @Volatile
    var graphOrNull: AppGraph? = null

    var graph: AppGraph
        get() = checkNotNull(graphOrNull) { "The application graph is not initialized" }
        set(value) {
            graphOrNull = value
        }
}

val appGraph: AppGraph get() = AppGraphHolder.graph
