package eu.kanade.tachiyomi.di

import io.mockk.mockk

val testAppGraph: AppGraph
    get() = AppGraphHolder.graphOrNull ?: mockk<AppGraph>(relaxed = true).also {
        AppGraphHolder.graph = it
    }
