package eu.kanade.presentation.more.settings.screen.player.custombutton

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.custombuttons.interactor.CreateCustomButton
import tachiyomi.domain.custombuttons.interactor.DeleteCustomButton
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.interactor.ReorderCustomButton
import tachiyomi.domain.custombuttons.interactor.ToggleFavoriteCustomButton
import tachiyomi.domain.custombuttons.interactor.UpdateCustomButton

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSettingsCustomButtonScreenModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `internal error event is delivered even when collector attaches late`() = runTest {
        val getCustomButtons = mockk<GetCustomButtons>()
        val createCustomButton = mockk<CreateCustomButton>()

        every { getCustomButtons.subscribeAll() } returns emptyFlow()
        coEvery {
            createCustomButton.await(
                "name",
                "content",
                "longPress",
                "startup",
            )
        } returns CreateCustomButton.Result.InternalError(RuntimeException("boom"))

        val model = PlayerSettingsCustomButtonScreenModel(
            getCustomButtons = getCustomButtons,
            createCustomButton = createCustomButton,
            deleteCustomButton = mockk<DeleteCustomButton>(relaxed = true),
            updateCustomButton = mockk<UpdateCustomButton>(relaxed = true),
            reorderCustomButton = mockk<ReorderCustomButton>(relaxed = true),
            toggleFavoriteCustomButton = mockk<ToggleFavoriteCustomButton>(relaxed = true),
        )

        model.createCustomButton("name", "content", "longPress", "startup")
        advanceUntilIdle()

        val event = withTimeout(1_000) {
            model.events.first()
        }

        assertEquals(CustomButtonEvent.InternalError, event)
        coVerify(exactly = 1) {
            createCustomButton.await("name", "content", "longPress", "startup")
        }
    }
}
