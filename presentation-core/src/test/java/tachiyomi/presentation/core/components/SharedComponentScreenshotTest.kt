package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w411dp-h891dp-xxhdpi"
private const val FOLDABLE = "w841dp-h701dp-xhdpi"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedComponentScreenshotTest {
    @Test
    @Config(qualifiers = PHONE)
    fun pillOnPhone() = capturePill()

    @Test
    @Config(qualifiers = FOLDABLE)
    fun pillOnFoldable() = capturePill()

    @Test
    @Config(qualifiers = PHONE)
    fun badgeOnPhone() = captureBadge()

    @Test
    @Config(qualifiers = FOLDABLE)
    fun badgeOnFoldable() = captureBadge()

    @Test
    @Config(qualifiers = PHONE)
    fun sectionCardOnPhone() = captureSectionCard()

    @Test
    @Config(qualifiers = FOLDABLE)
    fun sectionCardOnFoldable() = captureSectionCard()

    @Test
    @Config(qualifiers = PHONE)
    fun labeledCheckboxOnPhone() = captureLabeledCheckbox()

    @Test
    @Config(qualifiers = FOLDABLE)
    fun labeledCheckboxOnFoldable() = captureLabeledCheckbox()

    private fun capturePill() = capture {
        Pill(text = "Hello", style = MaterialTheme.typography.bodyMedium)
    }

    private fun captureBadge() = capture {
        Badge(text = "New")
    }

    private fun captureSectionCard() = capture {
        LazyColumn {
            item {
                SectionCard {
                    Text("Library settings")
                }
            }
        }
    }

    private fun captureLabeledCheckbox() = capture {
        LabeledCheckbox(label = "Show unread only", checked = true, onCheckedChange = {})
    }

    private fun capture(content: @Composable () -> Unit) = captureRoboImage {
        MaterialTheme {
            Surface(modifier = Modifier.padding(8.dp), content = content)
        }
    }
}
