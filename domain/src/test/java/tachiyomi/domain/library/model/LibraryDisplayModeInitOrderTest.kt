package tachiyomi.domain.library.model

import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class LibraryDisplayModeInitOrderTest {

    /**
     * Defines the LibraryDisplayMode classes itself so each test run controls
     * their initialization order. Everything else comes from the parent loader.
     */
    private class IsolatingLoader(parent: ClassLoader) : ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("tachiyomi.domain.library.model.LibraryDisplayMode")) {
                return super.loadClass(name, resolve)
            }
            findLoadedClass(name)?.let { return it }
            val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")
                ?.readBytes()
                ?: return super.loadClass(name, resolve)
            return defineClass(name, bytes, 0, bytes.size).also {
                if (resolve) resolveClass(it)
            }
        }
    }

    @Test
    fun `default resolves when a display mode initializes before the companion`() {
        val loader = IsolatingLoader(javaClass.classLoader!!)

        // Initialize the nested object FIRST. This is the order that used to
        // leave the companion's default field null.
        Class.forName("tachiyomi.domain.library.model.LibraryDisplayMode\$CompactGrid", true, loader)

        val outer = Class.forName("tachiyomi.domain.library.model.LibraryDisplayMode", true, loader)
        val companion = outer.getDeclaredField("Companion").get(null)
        val default = companion.javaClass.getMethod("getDefault").invoke(companion)

        default shouldNotBe null
    }
}
