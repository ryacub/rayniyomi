package eu.kanade.presentation.util

/** Removes the one-pane root while keeping the selected destination stack. */
fun <T> settingsStackForTwoPane(
    screens: List<T>,
    isMainScreen: (T) -> Boolean,
    defaultScreen: T,
): List<T> {
    if (screens.firstOrNull()?.let(isMainScreen) != true) return screens
    return if (screens.size == 1) listOf(defaultScreen) else screens.drop(1)
}

/** Adds the one-pane root before a destination when the stack enters one-pane mode. */
fun <T> settingsStackForSinglePane(
    screens: List<T>,
    isMainScreen: (T) -> Boolean,
    mainScreen: T,
): List<T> {
    return if (screens.firstOrNull()?.let(isMainScreen) == true) {
        screens
    } else {
        listOf(mainScreen) + screens
    }
}
