package tachiyomi.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import java.io.File

class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = BENCHMARK_TARGET_PACKAGE,
        profileBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            openMainNav("nav_library", "Manga")
            openMainNav("nav_updates", "Updates")
            openMainNav("nav_more", "More")
            openMainNav("nav_browse", "Browse")
            // Browse depth marker: first content render point.
            waitForAnyTextRequired("Sources", "Extensions", "Migrate")

            openMainNav("nav_more", "More")
            clickTextWithRetry("Discover")
            // Discover depth marker: first render point.
            waitForAnyTextRequired("For You", "Trending", "Recommendations")
            device.pressBack()

            // Novel flow: only deterministic when plugin section is present.
            openMainNav("nav_more", "More")
            clickTextWithRetry("Light Novels", required = false)
            if (device.wait(Until.hasObject(By.text("Light Novels")), 1_000)) {
                noteStep("Light Novels entry unavailable after retries; skipping novel marker")
            } else {
                waitForAnyTextOptional("Open Library", "Install", "Downloading", "Waiting for install")
                device.pressBack()
            }

            // Enrichment flow (best effort): attempt to enter a library detail screen.
            openMainNav("nav_library", "Manga")
            clickFirstCardIfPresent()
            waitForAnyTextOptional("Tracking", "Recommendations", "Related")
            device.pressBack()

            openMainNav("nav_more", "More")
            clickTextWithRetry("Settings")
        },
    )

    private fun MacrobenchmarkScope.openMainNav(menuId: String, fallbackText: String) {
        val byRes = By.res(BENCHMARK_TARGET_PACKAGE, menuId)
        val tab = device.wait(Until.findObject(byRes), FIND_TIMEOUT_MS)
            ?: device.findObject(By.text(fallbackText))
            ?: failStep("Unable to find main nav target id=$menuId text=$fallbackText")
        tab.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.clickTextWithRetry(text: String, required: Boolean = true) {
        repeat(FIND_RETRIES) { attempt ->
            val node = device.wait(Until.findObject(By.text(text)), FIND_TIMEOUT_MS)
            if (node != null) {
                node.click()
                device.waitForIdle()
                return
            }
            if (attempt < FIND_RETRIES - 1) {
                val x = device.displayWidth / 2
                val startY = (device.displayHeight * 0.85f).toInt()
                val endY = (device.displayHeight * 0.35f).toInt()
                device.swipe(x, startY, x, endY, 24)
                device.waitForIdle()
            }
        }
        if (required) failStep("Unable to click text='$text'")
    }

    private fun MacrobenchmarkScope.waitForAnyTextRequired(vararg texts: String) {
        val found = texts.any { text ->
            device.wait(Until.hasObject(By.textContains(text)), FIND_TIMEOUT_MS)
        }
        if (!found) failStep("None of required markers found: ${texts.joinToString()}")
    }

    private fun MacrobenchmarkScope.waitForAnyTextOptional(vararg texts: String) {
        val found = texts.any { text ->
            device.wait(Until.hasObject(By.textContains(text)), FIND_TIMEOUT_MS)
        }
        if (!found) noteStep("None of optional markers found: ${texts.joinToString()}")
    }

    private fun MacrobenchmarkScope.clickFirstCardIfPresent() {
        val candidates = listOf(
            By.res(BENCHMARK_TARGET_PACKAGE, "manga_library_item"),
            By.res(BENCHMARK_TARGET_PACKAGE, "anime_library_item"),
            By.descContains("cover"),
        )
        val hit = candidates.firstNotNullOfOrNull { device.wait(Until.findObject(it), 2_000) }
        hit?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.failStep(message: String): Nothing {
        captureDiag("baseline_profile_fail")
        error(message)
    }

    private fun MacrobenchmarkScope.noteStep(message: String) {
        println("BASELINE_PROFILE_NOTE: $message")
    }

    private fun MacrobenchmarkScope.captureDiag(prefix: String) {
        runCatching {
            val out = File("/sdcard/Download/${prefix}_${System.currentTimeMillis()}.png")
            device.takeScreenshot(out)
            println("BASELINE_PROFILE_DIAG_SCREENSHOT:${out.absolutePath}")
        }
    }
}

private const val FIND_TIMEOUT_MS = 5_000L
private const val FIND_RETRIES = 3
