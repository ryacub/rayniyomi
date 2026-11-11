package tachiyomi.presentation.core.components.material

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Extended FAB with the Material 3 Expressive collapse motion: the start padding is fixed, one
 * spring drives the width, and a faster spring fades the label.
 *
 * @see androidx.compose.material3.SmallExtendedFloatingActionButton
 */
@Composable
fun ExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        interactionSource = interactionSource,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
    ) {
        val expandTransition = updateTransition(if (expanded) 1f else 0f, label = "expanded")
        val widthProgress by expandTransition.animateFloat(
            transitionSpec = { spring(dampingRatio = 0.6f, stiffness = 800f) },
            label = "width",
        ) { it }
        val labelAlpha by expandTransition.animateFloat(
            transitionSpec = { spring(dampingRatio = 1f, stiffness = 3800f) },
            label = "labelAlpha",
        ) { it }
        val fullyCollapsed by remember(expandTransition) {
            derivedStateOf { expandTransition.currentState == 0f && !expandTransition.isRunning }
        }

        Row(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val expandedWidth = measurable.maxIntrinsicWidth(constraints.maxHeight)
                    val width = lerp(FabContainerSize.roundToPx(), expandedWidth, widthProgress)
                    val placeable = measurable.measure(constraints)
                    layout(width, placeable.height) { placeable.place(0, 0) }
                }
                .sizeIn(minWidth = FabContainerSize, minHeight = FabContainerSize)
                .padding(start = ExtendedFabStartPadding, end = ExtendedFabEndPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            if (!fullyCollapsed) {
                Row(modifier = Modifier.graphicsLayer { alpha = labelAlpha }) {
                    Spacer(Modifier.width(ExtendedFabIconPadding))
                    text()
                }
            }
        }
    }
}

private val FabContainerSize = 56.dp
private val ExtendedFabStartPadding = 16.dp
private val ExtendedFabEndPadding = 16.dp
private val ExtendedFabIconPadding = 8.dp
