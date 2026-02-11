package eu.kanade.tachiyomi.ui.browse.common.search

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchRequestCoordinatorTest {

    @Test
    fun `latest request id is accepted`() {
        val coordinator = SearchRequestCoordinator()

        val requestId = coordinator.nextRequestId()

        assertTrue(coordinator.isLatest(requestId))
    }

    @Test
    fun `older request id is rejected after newer request`() {
        val coordinator = SearchRequestCoordinator()

        val first = coordinator.nextRequestId()
        val second = coordinator.nextRequestId()

        assertFalse(coordinator.isLatest(first))
        assertTrue(coordinator.isLatest(second))
    }

    @Test
    fun `only the newest request stays valid across multiple updates`() {
        val coordinator = SearchRequestCoordinator()

        val first = coordinator.nextRequestId()
        val second = coordinator.nextRequestId()
        val third = coordinator.nextRequestId()

        assertFalse(coordinator.isLatest(first))
        assertFalse(coordinator.isLatest(second))
        assertTrue(coordinator.isLatest(third))
    }
}
