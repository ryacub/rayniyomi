package tachiyomi.domain.library.model

sealed interface LibraryDisplayMode {

    val preferenceKey: String

    data object CompactGrid : LibraryDisplayMode {
        override val preferenceKey: String = "COMPACT_GRID"
    }

    data object ComfortableGrid : LibraryDisplayMode {
        override val preferenceKey: String = "COMFORTABLE_GRID"
    }

    data object List : LibraryDisplayMode {
        override val preferenceKey: String = "LIST"
    }

    data object CoverOnlyGrid : LibraryDisplayMode {
        override val preferenceKey: String = "COVER_ONLY_GRID"
    }

    object Serializer {
        fun deserialize(serialized: String): LibraryDisplayMode {
            return Companion.deserialize(serialized)
        }

        fun serialize(value: LibraryDisplayMode): String {
            return value.serialize()
        }
    }

    companion object {
        val values by lazy { setOf(CompactGrid, ComfortableGrid, List, CoverOnlyGrid) }
        val default = CompactGrid

        fun fromPreferenceKey(preferenceKey: String): LibraryDisplayMode {
            return values.find { it.preferenceKey == preferenceKey } ?: default
        }

        fun deserialize(serialized: String): LibraryDisplayMode {
            return fromPreferenceKey(serialized)
        }
    }

    fun serialize(): String {
        return preferenceKey
    }
}
