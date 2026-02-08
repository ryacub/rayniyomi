package eu.kanade.tachiyomi.ui.player

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import eu.kanade.tachiyomi.ui.main.MainActivity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExternalIntentsTest {

    private lateinit var externalIntents: ExternalIntents

    @BeforeEach
    fun setup() {
        externalIntents = ExternalIntents()
    }

    @Test
    fun `registerActivity stores activity and launcher`() {
        val mockActivity = mockk<MainActivity>()
        val mockLauncher = mockk<ActivityResultLauncher<Intent>>()

        externalIntents.registerActivity(mockActivity, mockLauncher)

        externalIntents.getActiveActivity() shouldNotBe null
    }

    @Test
    fun `unregisterActivity clears activity and launcher`() {
        val mockActivity = mockk<MainActivity>()
        val mockLauncher = mockk<ActivityResultLauncher<Intent>>()

        externalIntents.registerActivity(mockActivity, mockLauncher)
        externalIntents.unregisterActivity()

        externalIntents.getActiveActivity() shouldBe null
    }

    @Test
    fun `registerActivity replaces previous registration`() {
        val firstActivity = mockk<MainActivity>()
        val firstLauncher = mockk<ActivityResultLauncher<Intent>>()
        val secondActivity = mockk<MainActivity>()
        val secondLauncher = mockk<ActivityResultLauncher<Intent>>()

        externalIntents.registerActivity(firstActivity, firstLauncher)
        externalIntents.registerActivity(secondActivity, secondLauncher)

        // Only second activity should be registered
        externalIntents.getActiveActivity() shouldBe secondActivity
    }

    @Test
    fun `launchExternalPlayer launches intent when activity registered`() {
        val mockActivity = mockk<MainActivity>()
        val mockLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        val mockIntent = mockk<Intent>()

        externalIntents.registerActivity(mockActivity, mockLauncher)
        val result = externalIntents.launchExternalPlayer(mockIntent)

        result shouldBe true
        verify { mockLauncher.launch(mockIntent) }
    }

    @Test
    fun `launchExternalPlayer returns false when no activity registered`() {
        val mockIntent = mockk<Intent>()

        val result = externalIntents.launchExternalPlayer(mockIntent)

        result shouldBe false
    }
}
