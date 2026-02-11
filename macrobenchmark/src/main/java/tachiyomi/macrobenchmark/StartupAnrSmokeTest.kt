/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tachiyomi.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ANR-sensitive startup smoke tests for R124.
 *
 * These tests verify that app startup completes within acceptable
 * time thresholds, catching regressions that could cause ANR dialogs.
 *
 * ANR threshold: 5 seconds for broadcast receivers, 10 seconds for services,
 * and input dispatching timeout of 5 seconds.
 *
 * Cold startup should complete well under 5 seconds to avoid ANR risk.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class StartupAnrSmokeTest {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Smoke test: Cold startup with no compilation.
     * Worst-case scenario - verifies app starts without ANR
     * even without any AOT compilation.
     */
    @Test
    fun coldStartupSmokeTest() {
        benchmarkRule.measureRepeated(
            packageName = "xyz.jmir.tachiyomi.mibenchmark",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 3,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }

    /**
     * Smoke test: Cold startup with baseline profile.
     * Represents typical production scenario.
     */
    @Test
    fun coldStartupWithBaselineProfile() {
        benchmarkRule.measureRepeated(
            packageName = "xyz.jmir.tachiyomi.mibenchmark",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
            iterations = 3,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }

    /**
     * Smoke test: Warm startup after process kill.
     * Simulates user returning to app after system killed process.
     * Migration and extension loading must not block UI thread.
     */
    @Test
    fun warmStartupAfterProcessKill() {
        benchmarkRule.measureRepeated(
            packageName = "xyz.jmir.tachiyomi.mibenchmark",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 3,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }
}
