package eu.kanade.tachiyomi.util.system

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class OrientationCapabilitiesTest {

    @ParameterizedTest
    @CsvSource(
        "35, 320, true",
        "35, 800, true",
        "36, 320, true",
        "36, 599, true",
        "36, 600, false",
        "36, 800, false",
        "37, 320, true",
        "37, 600, false",
        "99, 800, false",
        "36, 0, true",
    )
    fun `honorsOrientationRequests reports whether the platform honors orientation requests`(
        sdkInt: Int,
        smallestScreenWidthDp: Int,
        expected: Boolean,
    ) {
        honorsOrientationRequests(sdkInt, smallestScreenWidthDp) shouldBe expected
    }
}
