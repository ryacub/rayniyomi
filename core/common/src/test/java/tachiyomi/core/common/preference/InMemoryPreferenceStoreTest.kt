package tachiyomi.core.common.preference

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class InMemoryPreferenceStoreTest {

    // Basic get/set operations

    @Test
    fun `getString returns default when preference not set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default_value")

        pref.get() shouldBe "default_value"
    }

    @Test
    fun `getString returns stored value after set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default_value")

        pref.set("new_value")
        pref.get() shouldBe "new_value"
    }

    @Test
    fun `getLong returns default when preference not set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getLong("test_key", 42L)

        pref.get() shouldBe 42L
    }

    @Test
    fun `getLong returns stored value after set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getLong("test_key", 42L)

        pref.set(100L)
        pref.get() shouldBe 100L
    }

    @Test
    fun `getInt returns default when preference not set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getInt("test_key", 10)

        pref.get() shouldBe 10
    }

    @Test
    fun `getInt returns stored value after set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getInt("test_key", 10)

        pref.set(50)
        pref.get() shouldBe 50
    }

    @Test
    fun `getFloat returns default when preference not set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getFloat("test_key", 1.5f)

        pref.get() shouldBe 1.5f
    }

    @Test
    fun `getFloat returns stored value after set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getFloat("test_key", 1.5f)

        pref.set(3.14f)
        pref.get() shouldBe 3.14f
    }

    @Test
    fun `getBoolean returns default when preference not set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getBoolean("test_key", false)

        pref.get() shouldBe false
    }

    @Test
    fun `getBoolean returns stored value after set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getBoolean("test_key", false)

        pref.set(true)
        pref.get() shouldBe true
    }

    // isSet behavior

    @Test
    fun `isSet returns false initially`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default")

        pref.isSet() shouldBe false
    }

    @Test
    fun `isSet returns true after set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default")

        pref.set("value")
        pref.isSet() shouldBe true
    }

    // Delete behavior

    @Test
    fun `delete clears stored value`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default")

        pref.set("value")
        pref.delete()

        pref.get() shouldBe "default"
    }

    @Test
    fun `get returns default after delete`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getInt("test_key", 42)

        pref.set(100)
        pref.delete()

        pref.get() shouldBe 42
    }

    @Test
    fun `isSet returns false after delete`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default")

        pref.set("value")
        pref.delete()

        pref.isSet() shouldBe false
    }

    // Default values

    @Test
    fun `defaultValue returns correct default for String`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "my_default")

        pref.defaultValue() shouldBe "my_default"
    }

    @Test
    fun `defaultValue returns correct default for Long`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getLong("test_key", 999L)

        pref.defaultValue() shouldBe 999L
    }

    @Test
    fun `defaultValue returns correct default for Int`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getInt("test_key", 123)

        pref.defaultValue() shouldBe 123
    }

    @Test
    fun `defaultValue returns correct default for Float`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getFloat("test_key", 2.5f)

        pref.defaultValue() shouldBe 2.5f
    }

    @Test
    fun `defaultValue returns correct default for Boolean`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getBoolean("test_key", true)

        pref.defaultValue() shouldBe true
    }

    // Initial preferences

    @Test
    fun `constructor with initial preferences works`() {
        val initialPrefs = sequenceOf(
            InMemoryPreferenceStore.InMemoryPreference("key1", "value1", "default1"),
            InMemoryPreferenceStore.InMemoryPreference("key2", 42L, 0L),
        )
        val store = InMemoryPreferenceStore(initialPrefs)

        val pref1 = store.getString("key1", "default1")
        val pref2 = store.getLong("key2", 0L)

        pref1.get() shouldBe "value1"
        pref2.get() shouldBe 42L
    }

    @Test
    fun `initial preferences accessible via get`() {
        val initialPrefs = sequenceOf(
            InMemoryPreferenceStore.InMemoryPreference("stored_key", "stored_value", "default"),
        )
        val store = InMemoryPreferenceStore(initialPrefs)

        val pref = store.getString("stored_key", "default")

        pref.get() shouldBe "stored_value"
        pref.isSet() shouldBe true
    }

    // getObject behavior

    @Test
    fun `getObject returns default when preference not set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getObject(
            key = "test_key",
            defaultValue = "default_object",
            serializer = { it },
            deserializer = { it },
        )

        pref.get() shouldBe "default_object"
    }

    @Test
    fun `getObject returns stored value after set`() {
        val store = InMemoryPreferenceStore()
        val pref = store.getObject(
            key = "test_key",
            defaultValue = "default_object",
            serializer = { it },
            deserializer = { it },
        )

        pref.set("custom_object")
        pref.get() shouldBe "custom_object"
    }

    @Test
    fun `getObject works with custom types`() {
        val initialPrefs = sequenceOf(
            InMemoryPreferenceStore.InMemoryPreference("obj_key", "stored_custom", "default"),
        )
        val store = InMemoryPreferenceStore(initialPrefs)

        val pref = store.getObject(
            key = "obj_key",
            defaultValue = "default",
            serializer = { it.uppercase() },
            deserializer = { it.lowercase() },
        )

        pref.get() shouldBe "stored_custom"
    }

    // getAll behavior

    @Test
    fun `getAll returns empty map when no preferences set`() {
        val store = InMemoryPreferenceStore()

        val all = store.getAll()

        all.isEmpty() shouldBe true
    }

    @Test
    fun `getAll returns all preferences`() {
        val initialPrefs = sequenceOf(
            InMemoryPreferenceStore.InMemoryPreference("key1", "value1", "default1"),
            InMemoryPreferenceStore.InMemoryPreference("key2", 42L, 0L),
            InMemoryPreferenceStore.InMemoryPreference("key3", true, false),
        )
        val store = InMemoryPreferenceStore(initialPrefs)

        val all = store.getAll()

        all.size shouldBe 3
        all.containsKey("key1") shouldBe true
        all.containsKey("key2") shouldBe true
        all.containsKey("key3") shouldBe true
    }

    @Test
    fun `getAll includes only initial preferences not runtime preferences`() {
        val initialPrefs = sequenceOf(
            InMemoryPreferenceStore.InMemoryPreference("initial_key", "initial_value", "default"),
        )
        val store = InMemoryPreferenceStore(initialPrefs)

        // Create a new preference at runtime
        val runtimePref = store.getString("runtime_key", "default")
        runtimePref.set("runtime_value")

        val all = store.getAll()

        // getAll only returns initial preferences, not runtime ones
        all.size shouldBe 1
        all.containsKey("initial_key") shouldBe true
        all.containsKey("runtime_key") shouldBe false
    }

    // changes() behavior

    @Test
    fun `changes emits initial value on first collection`() = runBlocking {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default_value")

        val firstValue = pref.changes().first()

        firstValue shouldBe "default_value"
    }

    @Test
    fun `changes emits updated value after set`() = runBlocking {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default_value")

        pref.set("new_value")
        val emittedValue = pref.changes().first()

        emittedValue shouldBe "new_value"
    }

    @Test
    fun `changes emits default value after delete`() = runBlocking {
        val store = InMemoryPreferenceStore()
        val pref = store.getString("test_key", "default_value")

        pref.set("new_value")
        pref.delete()
        val emittedValue = pref.changes().first()

        emittedValue shouldBe "default_value"
    }

    @Test
    fun `changes emits to multiple collectors`() = runBlocking {
        val store = InMemoryPreferenceStore()
        val pref = store.getInt("test_key", 0)

        pref.set(42)

        val value1 = pref.changes().first()
        val value2 = pref.changes().first()

        value1 shouldBe 42
        value2 shouldBe 42
    }
}
