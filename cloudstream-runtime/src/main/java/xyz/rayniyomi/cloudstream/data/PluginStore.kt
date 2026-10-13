package xyz.rayniyomi.cloudstream.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** The manager's source of truth. [onChange] tells the host to reload its CloudStream sources. */
class PluginStore(private val file: File, private val onChange: () -> Unit) {

    private val mutableState =
        MutableStateFlow(if (file.isFile) PluginStateCodec.decode(file.readText()) else PluginState())

    val state: StateFlow<PluginState> = mutableState.asStateFlow()

    @Synchronized
    fun update(transform: (PluginState) -> PluginState): PluginState {
        val current = mutableState.value
        val next = transform(current)
        if (next != current) {
            file.parentFile?.mkdirs()
            writeAtomically(file, PluginStateCodec.encode(next).toByteArray())
            mutableState.value = next
            onChange()
        }
        return next
    }
}
