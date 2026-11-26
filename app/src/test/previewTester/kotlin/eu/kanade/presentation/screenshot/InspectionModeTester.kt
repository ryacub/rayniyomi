package eu.kanade.presentation.screenshot

import com.github.takahirom.roborazzi.AndroidComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.inspectionMode

@OptIn(ExperimentalRoborazziApi::class)
class InspectionModeTester :
    ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by AndroidComposePreviewTester(InspectionModeCapturer)

@OptIn(ExperimentalRoborazziApi::class)
private object InspectionModeCapturer : AndroidComposePreviewTester.Capturer {
    override fun capture(parameter: AndroidComposePreviewTester.CaptureParameter) {
        parameter.preview.captureRoboImage(
            parameter.filePath,
            parameter.roborazziOptions,
            parameter.roborazziComposeOptions.builder().inspectionMode(true).build(),
        )
    }
}
