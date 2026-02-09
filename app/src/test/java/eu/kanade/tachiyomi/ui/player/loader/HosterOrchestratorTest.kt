package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HosterOrchestratorTest {

    private lateinit var testScope: TestScope
    private lateinit var orchestrator: HosterOrchestrator

    @BeforeEach
    fun setup() {
        testScope = TestScope()
        orchestrator = HosterOrchestrator(testScope)
    }

    @Test
    fun `reset clears all state`() = runTest {
        // Given: orchestrator with some state
        orchestrator.updateIsLoadingHosters(false)

        // When: reset is called
        orchestrator.reset()

        // Then: all state is cleared
        assertEquals(emptyList<Hoster>(), orchestrator.hosterList.first())
        assertEquals(emptyList<HosterState>(), orchestrator.hosterState.first())
        assertEquals(emptyList<Boolean>(), orchestrator.hosterExpandedList.first())
        assertEquals(Pair(-1, -1), orchestrator.selectedHosterVideoIndex.first())
        assertNull(orchestrator.currentVideo.first())
    }

    @Test
    fun `updateIsLoadingHosters updates state`() = runTest {
        // Given: initial loading state is true
        assertEquals(true, orchestrator.isLoadingHosters.first())

        // When: updateIsLoadingHosters is called with false
        orchestrator.updateIsLoadingHosters(false)

        // Then: loading state is updated
        assertEquals(false, orchestrator.isLoadingHosters.first())
    }

    @Test
    fun `initial state is correct`() = runTest {
        // Then: initial state values are correct
        assertEquals(emptyList<Hoster>(), orchestrator.hosterList.first())
        assertEquals(true, orchestrator.isLoadingHosters.first())
        assertEquals(emptyList<HosterState>(), orchestrator.hosterState.first())
        assertEquals(emptyList<Boolean>(), orchestrator.hosterExpandedList.first())
        assertEquals(Pair(-1, -1), orchestrator.selectedHosterVideoIndex.first())
        assertNull(orchestrator.currentVideo.first())
    }

    @Test
    fun `cancelHosterVideoLinksJob does not throw`() {
        // When: cancelHosterVideoLinksJob is called without any active job
        // Then: it should not throw an exception
        orchestrator.cancelHosterVideoLinksJob()
    }
}
