package eu.kanade.tachiyomi.ui.browse.sourceprefs

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourcePreferencesContentAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private val prefsName = "source_pref_content_android_test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun sourcePreferencesContent_switchAndListPersistChanges() {
        val sharedPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val screen = buildSourcePreferenceScreen(
            context = context,
            sourcePreferences = sharedPrefs,
        ) { preferenceScreen: PreferenceScreen ->
            val switchPreference = SwitchPreferenceCompat(context).apply {
                key = "switch_key"
                title = "Switch Pref"
                summary = "Enable toggle"
                setDefaultValue(false)
            }
            val listPreference = ListPreference(context).apply {
                key = "list_key"
                title = "List Pref"
                entries = arrayOf("First", "Second")
                entryValues = arrayOf("first", "second")
                setDefaultValue("first")
            }

            preferenceScreen.addPreference(switchPreference)
            preferenceScreen.addPreference(listPreference)
        }

        composeRule.setContent {
            SourcePreferencesContent(
                preferenceScreen = screen,
                sourcePreferences = sharedPrefs,
                contentPadding = PaddingValues(),
            )
        }

        composeRule.onNodeWithText("Switch Pref").assertIsDisplayed().performClick()
        assertEquals(true, sharedPrefs.getBoolean("switch_key", false))

        composeRule.onNodeWithText("List Pref").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Second").assertIsDisplayed().performClick()
        assertEquals("second", sharedPrefs.getString("list_key", null))
    }
}
