package eu.kanade.tachiyomi.data.download.core

import android.content.Context
import android.os.PowerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BatteryOptimizationCheckerTest {

    private lateinit var context: Context
    private lateinit var powerManager: PowerManager
    private lateinit var checker: BatteryOptimizationChecker

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        powerManager = mockk()
        checker = BatteryOptimizationChecker(context, powerManager)
    }

    @Test
    fun `isOptimizationEnabled returns true when PowerManager says NOT ignoring (optimization enabled)`() {
        // PowerManager.isIgnoringBatteryOptimizations returns false means app is NOT exempted
        // which means optimization IS enabled for the app
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns false

        val result = checker.isOptimizationEnabled()

        assertTrue(result, "Should return true when app is not exempted from battery optimization")
    }

    @Test
    fun `isOptimizationEnabled returns false when PowerManager says IS ignoring (optimization disabled)`() {
        // PowerManager.isIgnoringBatteryOptimizations returns true means app IS exempted
        // which means optimization IS NOT enabled for the app
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns true

        val result = checker.isOptimizationEnabled()

        assertFalse(result, "Should return false when app is exempted from battery optimization")
    }

    @Test
    fun `isOptimizationEnabled defaults to true (optimization enabled) when PowerManager is null`() {
        // When PowerManager cannot be obtained, assume optimization IS enabled (safe default)
        checker = BatteryOptimizationChecker(context, null)

        val result = checker.isOptimizationEnabled()

        assertTrue(result, "Should default to true when PowerManager is unavailable")
    }

    @Test
    fun `isOptimizationEnabled uses the correct package name`() {
        val testPackageName = "com.example.test"
        every { context.packageName } returns testPackageName
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns false

        checker.isOptimizationEnabled()

        verify { powerManager.isIgnoringBatteryOptimizations(testPackageName) }
    }
}
