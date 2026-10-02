package tachiyomi.core.common.di

import android.content.Context

interface GraphProvider<out T> {
    val graph: T
}

@Suppress("UNCHECKED_CAST")
fun <T> Context.metroGraph(): T = (applicationContext as GraphProvider<T>).graph
