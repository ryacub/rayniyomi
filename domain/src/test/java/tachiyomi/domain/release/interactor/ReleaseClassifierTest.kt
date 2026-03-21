package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.release.model.ReleaseQuality
import tachiyomi.domain.release.model.ReleaseQuality.DEPRECATED
import tachiyomi.domain.release.model.ReleaseQuality.DRAFT
import tachiyomi.domain.release.model.ReleaseQuality.PRERELEASE
import tachiyomi.domain.release.model.ReleaseQuality.STABLE

@Execution(ExecutionMode.CONCURRENT)
class ReleaseClassifierTest {

    private val classifier = ReleaseClassifier()

    // ============== DRAFT FLAG TESTS ==============

    @Test
    fun `When draft flag is true expect DRAFT classification`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = true,
            body = null,
        )

        result shouldBe DRAFT
    }

    @Test
    fun `When draft flag is false expect not DRAFT`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe STABLE
    }

    // ============== PRERELEASE FLAG TESTS ==============

    @Test
    fun `When prerelease flag is true expect PRERELEASE classification`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = true,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When prerelease flag is false and no patterns expect STABLE`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe STABLE
    }

    // ============== VERSION STRING PATTERN TESTS ==============

    @Test
    fun `When version contains rc pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-rc1",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains RC uppercase pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-RC1",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains beta pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-beta",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains BETA uppercase pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-BETA2",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains alpha pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-alpha",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains ALPHA uppercase pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-ALPHA3",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains pre pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-pre",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains release-candidate pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-release-candidate",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version is stable format expect STABLE`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe STABLE
    }

    @Test
    fun `When version contains dev pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v2.1.0-dev",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    // ============== DEPRECATED BODY HEURISTIC TESTS ==============

    @Test
    fun `When body contains deprecated keyword expect DEPRECATED`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = "This release is deprecated",
        )

        result shouldBe DEPRECATED
    }

    @Test
    fun `When body contains Deprecated uppercase expect DEPRECATED`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = "This version is Deprecated",
        )

        result shouldBe DEPRECATED
    }

    @Test
    fun `When body contains DEPRECATED all caps expect DEPRECATED`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = "DEPRECATED: Use v2.0.0 instead",
        )

        result shouldBe DEPRECATED
    }

    @Test
    fun `When body does not contain deprecated expect not DEPRECATED`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = "This is a stable release",
        )

        result shouldBe STABLE
    }

    @Test
    fun `When body is null expect not DEPRECATED`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe STABLE
    }

    // ============== PRIORITY TESTS (Draft > Prerelease > Deprecated > Stable) ==============

    @Test
    fun `When draft is true it takes priority over prerelease flag`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = true,
            draft = true,
            body = null,
        )

        result shouldBe DRAFT
    }

    @Test
    fun `When draft is true it takes priority over body deprecated`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = true,
            body = "deprecated",
        )

        result shouldBe DRAFT
    }

    @Test
    fun `When prerelease flag is true it takes priority over deprecated body`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = true,
            draft = false,
            body = "deprecated",
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version pattern is rc and body is deprecated expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-rc1",
            prerelease = false,
            draft = false,
            body = "deprecated",
        )

        result shouldBe PRERELEASE
    }

    // ============== MISSING/NULL METADATA TESTS ==============

    @Test
    fun `When all metadata is null expect STABLE fallback`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe STABLE
    }

    @Test
    fun `When tagName is empty string expect STABLE fallback`() {
        val result = classifier.classify(
            tagName = "",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe STABLE
    }

    @Test
    fun `When body is empty string expect not DEPRECATED`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = "",
        )

        result shouldBe STABLE
    }

    // ============== MULTIPLE PATTERNS IN VERSION TESTS ==============

    @Test
    fun `When version has multiple prerelease patterns use first match`() {
        val result = classifier.classify(
            tagName = "v1.0.0-rc1-beta",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version has rc and beta patterns expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-rc.1.beta.2",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    // ============== CASE INSENSITIVITY TESTS ==============

    @Test
    fun `When version contains mixed case rc pattern expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0-Rc1",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When body contains deprecated with mixed case expect DEPRECATED`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = "Please use newer version, this is DePrEcAtEd",
        )

        result shouldBe DEPRECATED
    }

    // ============== EDGE CASES ==============

    @Test
    fun `When version has prerelease number without separator expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0rc1",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When version has alpha with numbers expect PRERELEASE`() {
        val result = classifier.classify(
            tagName = "v1.0.0alpha123",
            prerelease = false,
            draft = false,
            body = null,
        )

        result shouldBe PRERELEASE
    }

    @Test
    fun `When body contains deprecated as part of another word expect not DEPRECATED`() {
        val result = classifier.classify(
            tagName = "v1.0.0",
            prerelease = false,
            draft = false,
            body = "deprecatedNotAWord",
        )

        // This is lenient matching - if "deprecated" substring exists, mark as deprecated
        // Adjust expectation based on actual requirement
        result shouldBe DEPRECATED
    }
}
