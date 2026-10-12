package eu.kanade.presentation.more.settings.screen

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SettingsDataScreenCacheSizeAndroidTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dataPreferences_doNotReadChapterCacheOnMainThread() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = composeRule.activity
        val cache = appGraph.chapterCache
        val expectedSize = runBlocking(Dispatchers.IO) { cache.readableSize }
        val expectedSubtitle = context.stringResource(MR.strings.used_cache, expectedSize)
        val cacheTitle = context.stringResource(AYMR.strings.pref_clear_chapter_cache)
        val violations = CopyOnWriteArrayList<Violation>()
        val preferences = AtomicReference<List<Preference>>(emptyList())
        var previousPolicy: StrictMode.ThreadPolicy? = null

        instrumentation.runOnMainSync {
            previousPolicy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .penaltyListener({ it.run() }) { violations.add(it) }
                    .build(),
            )
            context.cacheDir.exists()
        }

        try {
            instrumentation.waitForIdleSync()
            assertFalse("The Main disk-read listener must detect the calibration read", violations.isEmpty())
            violations.clear()

            composeRule.setContent {
                MaterialTheme {
                    Navigator(SettingsDataScreen) {
                        val current = SettingsDataScreen.getPreferences()
                        SideEffect { preferences.set(current) }
                        Text("Data preferences")
                    }
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                preferences.get()
                    .filterIsInstance<Preference.PreferenceGroup>()
                    .flatMap { it.preferenceItems }
                    .filterIsInstance<Preference.PreferenceItem.TextPreference>()
                    .any { it.title == cacheTitle && it.subtitle == expectedSubtitle }
            }
            composeRule.waitForIdle()
            instrumentation.waitForIdleSync()

            val cacheReads = violations.filter { violation ->
                violation.stackTrace.any {
                    it.className == ChapterCache::class.java.name && it.methodName == "getReadableSize"
                } && violation.stackTrace.any { it.methodName == "getDirectorySize" }
            }
            assertEquals("Chapter cache size must not read disk on Main", emptyList<Violation>(), cacheReads)
        } finally {
            instrumentation.runOnMainSync { StrictMode.setThreadPolicy(checkNotNull(previousPolicy)) }
        }
    }
}
