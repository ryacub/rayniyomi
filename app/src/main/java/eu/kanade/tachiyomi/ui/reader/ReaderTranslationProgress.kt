package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.translation.TranslationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Resolves the [TranslationState] for the open chapter.
 *
 * A null chapter id or an absent map key maps to [TranslationState.Idle]:
 * TranslationManager removes entries when a chapter returns to Idle, so the
 * elvis default is the canonical Idle source. No deduplication is applied:
 * a switch between two chapters with equal states must still emit, or the
 * previous chapter's numbers would stay on screen.
 */
fun translationStateFlow(
    states: Flow<Map<Long, TranslationState>>,
    chapterIds: Flow<Long?>,
): Flow<TranslationState> = combine(states, chapterIds) { map, id -> map[id] ?: TranslationState.Idle }
