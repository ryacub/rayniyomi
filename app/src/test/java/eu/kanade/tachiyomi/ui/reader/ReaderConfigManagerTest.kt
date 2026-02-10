package eu.kanade.tachiyomi.ui.reader

/**
 * Tests removed - ReaderConfigManager requires Robolectric for unit testing
 * due to dependencies on Android framework classes (Color, ColorMatrix, WindowManager, Uri).
 *
 * To add tests in the future, either:
 * 1. Add Robolectric dependency for Android framework mocking
 * 2. Move to instrumented tests (androidTest/)
 * 3. Refactor ReaderConfigManager to inject Android dependencies
 */
@Suppress("ktlint:standard:filename")
internal object ReaderConfigManagerTest
