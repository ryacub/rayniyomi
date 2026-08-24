package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager.GestureInputMode

/**
 * Arbitrates the pager gesture input mode between independent owners. Each owner
 * holds a named claim while it needs a non-default mode. The effective mode is
 * always the strongest active claim's mode, so one owner releasing its claim can
 * never end another owner's suppression window.
 */
class GestureInputGate {

    /**
     * A named reason for constraining the pager gestures, with the mode it requests.
     */
    enum class Claim(internal val requestedMode: GestureInputMode) {
        /** A page curl owns the screen and the reader chrome must stay hidden. */
        CURL(GestureInputMode.SUPPRESS_CHROME),

        /** A pager child button is pressed and must own the touch stream. */
        BUTTON_PRESS(GestureInputMode.DISABLED),

        /** An error-layout action is pressed and must own the touch stream. */
        DIALOG_PRESS(GestureInputMode.DISABLED),
    }

    private val activeClaims = mutableSetOf<Claim>()

    /**
     * The strongest active claim's mode, or [GestureInputMode.ENABLED] when no claim is active.
     */
    val effectiveMode: GestureInputMode
        get() = activeClaims.maxOfOrNull { it.requestedMode } ?: GestureInputMode.ENABLED

    /**
     * Adds [claim] to the active claims. Acquiring an active claim changes nothing.
     */
    fun acquire(claim: Claim) {
        activeClaims.add(claim)
    }

    /**
     * Removes [claim] from the active claims. Releasing an inactive claim does nothing.
     */
    fun release(claim: Claim) {
        activeClaims.remove(claim)
    }
}
