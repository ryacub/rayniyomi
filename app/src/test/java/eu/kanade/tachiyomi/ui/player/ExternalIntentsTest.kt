package eu.kanade.tachiyomi.ui.player

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import eu.kanade.tachiyomi.ui.main.MainActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

class ExternalIntentsTest {

    @Mock
    private lateinit var mockActivity: MainActivity

    @Mock
    private lateinit var mockLauncher: ActivityResultLauncher<Intent>

    private lateinit var externalIntents: ExternalIntents

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        externalIntents = ExternalIntents()
    }

    @Test
    fun `registerActivity stores activity and launcher`() {
        externalIntents.registerActivity(mockActivity, mockLauncher)

        // Should not throw when accessing registered activity
        assertNotNull(externalIntents.getActiveActivity())
    }

    @Test
    fun `unregisterActivity clears activity and launcher`() {
        externalIntents.registerActivity(mockActivity, mockLauncher)
        externalIntents.unregisterActivity()

        assertNull(externalIntents.getActiveActivity())
    }

    @Test
    fun `registerActivity replaces previous registration`() {
        val secondActivity: MainActivity = mock(MainActivity::class.java)
        val secondLauncher: ActivityResultLauncher<Intent> = mock()

        externalIntents.registerActivity(mockActivity, mockLauncher)
        externalIntents.registerActivity(secondActivity, secondLauncher)

        // Only second activity should be registered
        assertEquals(secondActivity, externalIntents.getActiveActivity())
    }

    @Test
    fun `launchExternalPlayer launches intent when activity registered`() {
        val mockIntent: Intent = mock(Intent::class.java)
        externalIntents.registerActivity(mockActivity, mockLauncher)

        val result = externalIntents.launchExternalPlayer(mockIntent)

        assertEquals(true, result)
        org.mockito.Mockito.verify(mockLauncher).launch(mockIntent)
    }

    @Test
    fun `launchExternalPlayer returns false when no activity registered`() {
        val mockIntent: Intent = mock(Intent::class.java)

        val result = externalIntents.launchExternalPlayer(mockIntent)

        assertEquals(false, result)
    }
}
