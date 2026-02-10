package eu.kanade.tachiyomi.extension

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExtensionReconciliationTest {

    @Test
    fun `selects candidate by signature hash when available`() {
        val candidates = listOf(
            TestCandidate(signature = "sig-a", repo = "https://repo-a"),
            TestCandidate(signature = "sig-b", repo = "https://repo-b"),
        )

        val selected = selectPreferredExtensionCandidate(
            candidates = candidates,
            installedSignatureHash = "sig-b",
            installedRepoUrl = "https://repo-a",
            candidateSignatureHash = TestCandidate::signature,
            candidateRepoUrl = TestCandidate::repo,
        )

        assertEquals(candidates[1], selected)
    }

    @Test
    fun `falls back to repo url when signature hash does not match`() {
        val candidates = listOf(
            TestCandidate(signature = "sig-a", repo = "https://repo-a"),
            TestCandidate(signature = "sig-b", repo = "https://repo-b"),
        )

        val selected = selectPreferredExtensionCandidate(
            candidates = candidates,
            installedSignatureHash = "sig-z",
            installedRepoUrl = "https://repo-b",
            candidateSignatureHash = TestCandidate::signature,
            candidateRepoUrl = TestCandidate::repo,
        )

        assertEquals(candidates[1], selected)
    }

    @Test
    fun `falls back to first candidate when no signature or repo match`() {
        val candidates = listOf(
            TestCandidate(signature = "sig-a", repo = "https://repo-a"),
            TestCandidate(signature = "sig-b", repo = "https://repo-b"),
        )

        val selected = selectPreferredExtensionCandidate(
            candidates = candidates,
            installedSignatureHash = "sig-z",
            installedRepoUrl = "https://repo-z",
            candidateSignatureHash = TestCandidate::signature,
            candidateRepoUrl = TestCandidate::repo,
        )

        assertEquals(candidates[0], selected)
    }

    @Test
    fun `falls back to repo url when installed signature is null`() {
        val candidates = listOf(
            TestCandidate(signature = "sig-a", repo = "https://repo-a"),
            TestCandidate(signature = "sig-b", repo = "https://repo-b"),
        )

        val selected = selectPreferredExtensionCandidate(
            candidates = candidates,
            installedSignatureHash = null,
            installedRepoUrl = "https://repo-b",
            candidateSignatureHash = TestCandidate::signature,
            candidateRepoUrl = TestCandidate::repo,
        )

        assertEquals(candidates[1], selected)
    }

    @Test
    fun `falls back to first candidate when installed signature and repo are null`() {
        val candidates = listOf(
            TestCandidate(signature = "sig-a", repo = "https://repo-a"),
            TestCandidate(signature = "sig-b", repo = "https://repo-b"),
        )

        val selected = selectPreferredExtensionCandidate(
            candidates = candidates,
            installedSignatureHash = null,
            installedRepoUrl = null,
            candidateSignatureHash = TestCandidate::signature,
            candidateRepoUrl = TestCandidate::repo,
        )

        assertEquals(candidates[0], selected)
    }

    @Test
    fun `returns only candidate when there is one`() {
        val candidates = listOf(
            TestCandidate(signature = "sig-a", repo = "https://repo-a"),
        )

        val selected = selectPreferredExtensionCandidate(
            candidates = candidates,
            installedSignatureHash = "sig-z",
            installedRepoUrl = "https://repo-z",
            candidateSignatureHash = TestCandidate::signature,
            candidateRepoUrl = TestCandidate::repo,
        )

        assertEquals(candidates[0], selected)
    }

    @Test
    fun `throws for empty candidate list`() {
        assertThrows(IllegalArgumentException::class.java) {
            selectPreferredExtensionCandidate(
                candidates = emptyList(),
                installedSignatureHash = "sig-a",
                installedRepoUrl = "https://repo-a",
                candidateSignatureHash = TestCandidate::signature,
                candidateRepoUrl = TestCandidate::repo,
            )
        }
    }

    private data class TestCandidate(
        val signature: String,
        val repo: String,
    )
}
