package eu.kanade.presentation.more.settings.screen.translation

import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import eu.kanade.tachiyomi.data.translation.catalog.TranslationCatalogResult
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCapabilities
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCatalog
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCatalogRepository
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoiceType
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCost
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelEntry
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelStability
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationModelPickerScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load success populates models and clears loading`() {
        val fixture = Fixture(modelId = "", choiceType = TranslationModelChoiceType.AUTOMATIC)
        val model = fixture.model(success = success(listOf(visionModel("a"))))

        model.awaitSettled()

        val state = model.state.value
        assertEquals(listOf("a"), state.pickerState.models.map { it.id })
        assertFalse(state.pickerState.isLoading)
        assertNull(state.pickerState.errorMessage)
    }

    @Test
    fun `load failure keeps cached models and sets error message`() {
        val fixture = Fixture(modelId = "", choiceType = TranslationModelChoiceType.AUTOMATIC)
        val model = fixture.model(
            TranslationCatalogResult.Failure(reason = "boom", cachedModels = listOf(visionModel("cached"))),
        )

        model.awaitSettled()

        val state = model.state.value
        assertEquals(listOf("cached"), state.pickerState.models.map { it.id })
        assertEquals("boom", state.pickerState.errorMessage)
    }

    @Test
    fun `load automatic choice writes resolved model id`() {
        val fixture = Fixture(modelId = "", choiceType = TranslationModelChoiceType.AUTOMATIC)
        val model = fixture.model(success = success(listOf(visionModel("auto"))))

        model.awaitSettled()

        assertEquals("auto", fixture.modelPref.get())
    }

    @Test
    fun `load automatic choice with no compatible model writes empty model id`() {
        val fixture = Fixture(modelId = "stale", choiceType = TranslationModelChoiceType.AUTOMATIC)
        val model = fixture.model(success = success(emptyList()))

        model.awaitSettled()

        assertEquals("", fixture.modelPref.get())
    }

    @Test
    fun `load restores saved pinned choice type`() {
        val fixture = Fixture(modelId = "pinned-id", choiceType = TranslationModelChoiceType.PINNED)
        val model = fixture.model(success = success(emptyList()))

        model.awaitSettled()

        assertEquals(TranslationModelChoiceType.PINNED, model.state.value.choiceType)
        assertEquals("pinned-id", model.state.value.selectedModelId)
    }

    @Test
    fun `select model writes id and pinned choice type`() {
        val fixture = Fixture(modelId = "", choiceType = TranslationModelChoiceType.AUTOMATIC)
        val model = fixture.model()

        model.awaitSettled()
        model.selectModel("model-id")

        assertEquals("model-id", fixture.modelPref.get())
        assertEquals(TranslationModelChoiceType.PINNED, fixture.choicePref.get())
        assertEquals(TranslationModelChoiceType.PINNED, model.state.value.choiceType)
        assertEquals("model-id", model.state.value.selectedModelId)
    }

    @Test
    fun `select automatic sets automatic and re-resolves id`() {
        val fixture = Fixture(modelId = "", choiceType = TranslationModelChoiceType.AUTOMATIC)
        val model = fixture.model(success = success(listOf(visionModel("auto"))))

        model.awaitSettled()
        model.selectAutomatic()

        assertEquals(TranslationModelChoiceType.AUTOMATIC, fixture.choicePref.get())
        assertEquals("auto", fixture.modelPref.get())
        assertEquals(TranslationModelChoiceType.AUTOMATIC, model.state.value.choiceType)
    }

    @Test
    fun `refresh calls load catalog with force refresh true`() {
        val fixture = Fixture(modelId = "", choiceType = TranslationModelChoiceType.AUTOMATIC)
        val order = mutableListOf<Boolean>()
        val model = fixture.model(
            successes = listOf(success(emptyList()), success(emptyList())),
            onLoad = { forceRefresh -> order.add(forceRefresh) },
        )

        runBlocking {
            withTimeout(5_000) {
                model.state.first { !it.pickerState.isLoading }
                model.refresh()
                model.state.first { it.pickerState.isLoading }
                model.state.first { !it.pickerState.isLoading }
            }
        }

        assertEquals(listOf(false, true), order)
    }

    @Test
    fun `refresh while loading is reflected in state loading`() {
        val gate = CompletableDeferred<Unit>()
        var loadCount = 0
        val fixture = Fixture(modelId = "", choiceType = TranslationModelChoiceType.AUTOMATIC)
        val model = fixture.model(
            loadCatalog = { _, _, _ ->
                loadCount++
                if (loadCount == 1) {
                    success(emptyList())
                } else {
                    gate.await()
                    success(emptyList())
                }
            },
        )

        runBlocking {
            withTimeout(5_000) {
                model.state.first { !it.pickerState.isLoading }
                model.refresh()
                // The second load begins and is held on the gate, so loading stays visible.
                model.state.first { it.pickerState.isLoading }
                assertTrue(model.state.value.pickerState.isLoading)
                gate.complete(Unit)
                model.state.first { !it.pickerState.isLoading }
            }
        }
    }

    private fun success(models: List<TranslationModelEntry>) = TranslationCatalogResult.Success(
        catalog = TranslationModelCatalog(
            provider = TranslationProvider.CLAUDE,
            fetchedAtEpochMilliseconds = 1_000,
            models = models,
        ),
        fromCache = false,
    )

    private fun TranslationModelPickerScreenModel.awaitSettled() {
        runBlocking {
            withTimeout(5_000) {
                state.first { !it.pickerState.isLoading }
            }
        }
    }

    private inner class Fixture(
        val modelId: String = "",
        val choiceType: TranslationModelChoiceType = TranslationModelChoiceType.AUTOMATIC,
    ) {
        val modelPref = statefulPref(modelId)
        val choicePref = statefulPref(choiceType)
        private val providerPref = statefulPref(TranslationProvider.CLAUDE)
        private val apiKeyPref = statefulPref("")

        private val preferences = mockk<TranslationPreferences> {
            every { translationProvider() } returns providerPref
            every { translationApiKey(any()) } returns apiKeyPref
            every { translationModel(any()) } returns modelPref
            every { translationModelChoiceType(any()) } returns choicePref
        }
        private val repository = mockk<TranslationModelCatalogRepository>(relaxed = true)

        fun model(
            success: TranslationCatalogResult? = null,
            successes: List<TranslationCatalogResult> = emptyList(),
            loadCatalog: (suspend (TranslationProvider, String, Boolean) -> TranslationCatalogResult)? = null,
            onLoad: (Boolean) -> Unit = {},
        ): TranslationModelPickerScreenModel {
            val queue = if (success != null) ArrayDeque(listOf(success)) else ArrayDeque(successes)
            val loader: suspend (TranslationProvider, String, Boolean) -> TranslationCatalogResult =
                loadCatalog ?: { _, _, forceRefresh ->
                    onLoad(forceRefresh)
                    if (queue.isEmpty()) success(emptyList()) else queue.removeFirst()
                }
            return TranslationModelPickerScreenModel(
                preferences = preferences,
                repository = repository,
                loadCatalog = loader,
            )
        }
    }

    private inline fun <reified T> statefulPref(default: T): Preference<T> {
        val holder = Holder(default)
        return mockk(relaxed = true) {
            every { get() } answers { holder.value }
            every { set(any()) } answers { holder.value = firstArg() }
        }
    }

    private class Holder<T>(var value: T)

    private fun visionModel(id: String, imageInput: Boolean = true) = TranslationModelEntry(
        id = id,
        displayName = id,
        capabilities = TranslationModelCapabilities(
            imageInput = imageInput,
            textOutput = true,
            multilingualOcrAndTranslation = false,
            spatialBounds = false,
            normalizedCoordinates = false,
            originalAndTranslatedFields = false,
            maxOutputTokens = 4_096,
            structuredJsonOutput = false,
        ),
        cost = TranslationModelCost.PAID,
        freeTierEligible = null,
        stability = TranslationModelStability.STABLE,
        dataTerms = null,
    )
}
