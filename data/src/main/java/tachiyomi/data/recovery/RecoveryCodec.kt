package tachiyomi.data.recovery

import kotlinx.serialization.json.Json
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryItemPair
import tachiyomi.domain.recovery.model.RecoveryState

internal object RecoveryCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeSourceIds(ids: List<Long>): String = ids.joinToString(",")

    fun decodeSourceIds(value: String): List<Long> = value.split(',').mapNotNull { it.toLongOrNull() }

    fun encodeCandidates(candidates: List<RecoveryCandidate>): String = json.encodeToString(candidates)

    fun decodeCandidates(value: String): List<RecoveryCandidate> =
        runCatching { json.decodeFromString<List<RecoveryCandidate>>(value) }.getOrDefault(emptyList())

    fun encodeErrors(errors: Map<Long, String>): String = json.encodeToString(errors)

    fun decodeErrors(value: String): Map<Long, String> =
        runCatching { json.decodeFromString<Map<Long, String>>(value) }.getOrDefault(emptyMap())

    fun encodeChoice(choice: RecoveryCandidate): String = json.encodeToString(choice)

    fun decodeChoice(value: String?): RecoveryCandidate? =
        value?.let { runCatching { json.decodeFromString<RecoveryCandidate>(it) }.getOrNull() }

    fun encodePairs(pairs: List<RecoveryItemPair>): String = json.encodeToString(pairs)

    fun decodePairs(value: String): List<RecoveryItemPair> =
        runCatching { json.decodeFromString<List<RecoveryItemPair>>(value) }.getOrDefault(emptyList())

    fun names(states: Set<RecoveryState>): List<String> = states.map { it.name }

    @Suppress("LongParameterList")
    fun entry(
        id: Long,
        sessionId: Long,
        originalId: Long,
        selected: Boolean,
        state: String,
        searchQuery: String,
        candidates: String,
        searchErrors: String,
        choice: String?,
        pairs: String,
        resultId: Long?,
        error: String?,
        runToken: String?,
    ) = RecoveryEntry(
        id = id,
        sessionId = sessionId,
        originalId = originalId,
        selected = selected,
        state = RecoveryState.entries.firstOrNull { it.name == state } ?: RecoveryState.INTERRUPTED,
        searchQuery = searchQuery,
        candidates = decodeCandidates(candidates),
        searchErrors = decodeErrors(searchErrors),
        choice = decodeChoice(choice),
        pairs = decodePairs(pairs),
        resultId = resultId,
        error = error,
        runToken = runToken,
    )
}
