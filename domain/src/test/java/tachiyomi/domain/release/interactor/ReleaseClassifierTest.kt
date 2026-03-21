package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.release.model.ReleaseQuality.PRERELEASE
import tachiyomi.domain.release.model.ReleaseQuality.STABLE

@Execution(ExecutionMode.CONCURRENT)
class ReleaseClassifierTest {

    private val classifier = ReleaseClassifier()

    // ============== PRERELEASE FLAG TESTS ==============

    @Test
    fun `When prerelease flag is true expect PRERELEASE classification`() {
        val result = classifier.classify(tagName = "v1.0.0", prerelease = true)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When prerelease flag is false and no patterns expect STABLE`() {
        val result = classifier.classify(tagName = "v1.0.0", prerelease = false)
        result shouldBe STABLE
    }

    // ============== VERSION STRING PATTERN TESTS ==============

    @Test
    fun `When version contains rc pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-rc1", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains RC uppercase pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-RC1", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains beta pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-beta", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains BETA uppercase pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-BETA2", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains alpha pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-alpha", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains ALPHA uppercase pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-ALPHA3", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains pre pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-pre", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version contains release-candidate pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-release-candidate", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version is stable format expect STABLE`() {
        val result = classifier.classify(tagName = "v1.0.0", prerelease = false)
        result shouldBe STABLE
    }

    @Test
    fun `When version contains dev pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v2.1.0-dev", prerelease = false)
        result shouldBe PRERELEASE
    }

    // ============== PRIORITY TESTS ==============

    @Test
    fun `When prerelease flag is true it takes priority over stable tag`() {
        val result = classifier.classify(tagName = "v1.0.0", prerelease = true)
        result shouldBe PRERELEASE
    }

    // ============== MISSING/NULL METADATA TESTS ==============

    @Test
    fun `When tagName is empty string expect STABLE fallback`() {
        val result = classifier.classify(tagName = "", prerelease = false)
        result shouldBe STABLE
    }

    // ============== MULTIPLE PATTERNS IN VERSION TESTS ==============

    @Test
    fun `When version has multiple prerelease patterns expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-rc1-beta", prerelease = false)
        result shouldBe PRERELEASE
    }

    // ============== CASE INSENSITIVITY TESTS ==============

    @Test
    fun `When version contains mixed case rc pattern expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0-Rc1", prerelease = false)
        result shouldBe PRERELEASE
    }

    // ============== EDGE CASES ==============

    @Test
    fun `When version has prerelease number without separator expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0rc1", prerelease = false)
        result shouldBe PRERELEASE
    }

    @Test
    fun `When version has alpha with numbers expect PRERELEASE`() {
        val result = classifier.classify(tagName = "v1.0.0alpha123", prerelease = false)
        result shouldBe PRERELEASE
    }
}
