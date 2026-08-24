package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.viewer.pager.GestureInputGate.Claim.BUTTON_PRESS
import eu.kanade.tachiyomi.ui.reader.viewer.pager.GestureInputGate.Claim.CURL
import eu.kanade.tachiyomi.ui.reader.viewer.pager.GestureInputGate.Claim.DIALOG_PRESS
import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager.GestureInputMode.DISABLED
import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager.GestureInputMode.ENABLED
import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager.GestureInputMode.SUPPRESS_CHROME
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GestureInputGateTest {

    @Test
    fun `empty gate reports enabled`() {
        GestureInputGate().effectiveMode shouldBe ENABLED
    }

    @Test
    fun `curl claim suppresses chrome`() {
        val gate = GestureInputGate()

        gate.acquire(CURL)

        gate.effectiveMode shouldBe SUPPRESS_CHROME
    }

    @Test
    fun `press claims disable input`() {
        val buttonGate = GestureInputGate()
        val dialogGate = GestureInputGate()

        buttonGate.acquire(BUTTON_PRESS)
        dialogGate.acquire(DIALOG_PRESS)

        buttonGate.effectiveMode shouldBe DISABLED
        dialogGate.effectiveMode shouldBe DISABLED
    }

    @Test
    fun `disabled beats suppress chrome`() {
        val gate = GestureInputGate()
        gate.acquire(CURL)
        gate.acquire(BUTTON_PRESS)

        gate.effectiveMode shouldBe DISABLED
    }

    @Test
    fun `suppress chrome beats enabled`() {
        val gate = GestureInputGate()
        gate.acquire(CURL)

        gate.effectiveMode shouldBe SUPPRESS_CHROME
    }

    @Test
    fun `release falls back to the strongest remaining claim`() {
        val gate = GestureInputGate()
        gate.acquire(CURL)
        gate.acquire(DIALOG_PRESS)

        gate.release(DIALOG_PRESS)

        gate.effectiveMode shouldBe SUPPRESS_CHROME
    }

    @Test
    fun `release of the only claim returns to enabled`() {
        val gate = GestureInputGate()
        gate.acquire(CURL)

        gate.release(CURL)

        gate.effectiveMode shouldBe ENABLED
    }

    @Test
    fun `acquiring an active claim is idempotent`() {
        val gate = GestureInputGate()
        gate.acquire(CURL)

        gate.acquire(CURL)
        gate.release(CURL)

        gate.effectiveMode shouldBe ENABLED
    }

    @Test
    fun `releasing an inactive claim is a no-op`() {
        val gate = GestureInputGate()
        gate.acquire(CURL)

        gate.release(BUTTON_PRESS)

        gate.effectiveMode shouldBe SUPPRESS_CHROME
    }
}
