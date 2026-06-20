import unittest
from pathlib import Path


class ReaderParityWorkflowTest(unittest.TestCase):
    def test_reader_smoke_runs_as_plain_junit_instrumentation(self):
        workflow = Path(".github/workflows/reader_parity_gate.yml").read_text()

        self.assertIn(":reader-parity:connectedBenchmarkAndroidTest", workflow)
        self.assertIn(
            "-Pandroid.testInstrumentationRunnerArguments.class=tachiyomi.readerparity.ReaderParitySmokeTest",
            workflow,
        )
        self.assertNotIn(":macrobenchmark:connectedBenchmarkAndroidTest", workflow)
        self.assertNotIn("ReaderParitySmokeBenchmark", workflow)
        self.assertNotIn("connected_android_test_additional_output", workflow)

    def test_reader_smoke_does_not_initialize_androidx_benchmark(self):
        smoke_source = Path(
            "reader-parity/src/main/java/tachiyomi/readerparity/ReaderParitySmokeTest.kt",
        ).read_text()

        self.assertNotIn("MacrobenchmarkRule", smoke_source)
        self.assertNotIn("FrameTimingMetric", smoke_source)
        self.assertNotIn("CompilationMode", smoke_source)
        self.assertNotIn("measureRepeated", smoke_source)

    def test_reader_parity_module_has_no_benchmark_dependency(self):
        build_file = Path("reader-parity/build.gradle.kts").read_text()

        self.assertIn('id("com.android.test")', build_file)
        self.assertIn('targetProjectPath = ":app"', build_file)
        self.assertIn('it.buildType == "benchmark"', build_file)
        self.assertNotIn('id("mihon.benchmark")', build_file)
        self.assertNotIn("androidx.benchmark", build_file)


if __name__ == "__main__":
    unittest.main()
