package shark

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

@Composable
fun GridLayout(
  modifier: Modifier = Modifier,
  scrollState: ScrollState = rememberScrollState(0f),
  itemWidth: Dp,
  content: @Composable() () -> Unit
) {
  Layout(
    modifier = modifier.verticalScroll(
      state = scrollState,
      enabled = true
    ),
    content = content
  ) { measurables, constraints ->
    if (measurables.isEmpty()) {
      return@Layout layout(0, 0) {}
    }

    val layoutWidth = constraints.maxWidth

    val itemWidthPx = itemWidth.toIntPx()

    val itemHeightPx = measurables.maxOf { it.minIntrinsicHeight(itemWidthPx) }

    val childConstraints = Constraints.fixed(itemWidthPx, itemHeightPx)
    val placeables = measurables.map { measurable ->
      measurable.measure(childConstraints)
    }

    val minSpacing = 24.dp.toIntPx()

    val columnCount =
      floor((layoutWidth - minSpacing) / (itemWidthPx + minSpacing).toFloat()).toInt()

    // Not enough space.
    if (columnCount == 0) {
      return@Layout layout(0, 0) {}
    }

    val rowCount = ceil(placeables.size / columnCount.toFloat()).toInt()
    val horizontalSpacing =
      floor((layoutWidth - (columnCount * itemWidthPx)) / (columnCount + 1).toFloat()).toInt()
    val remainingPixels =
      layoutWidth - (horizontalSpacing + columnCount * (itemWidthPx + horizontalSpacing))
    val evenRemainingPixels = remainingPixels % 2 == 0
    val leftPadding =
      horizontalSpacing + if (evenRemainingPixels) remainingPixels / 2 else ((remainingPixels - 1) / 2) + 1
    val verticalSpacing = min(horizontalSpacing, 48.dp.toIntPx())

    val topBottomPadding = verticalSpacing

    val layoutHeight =
      2 * topBottomPadding + (rowCount * itemHeightPx) + (rowCount - 1) * verticalSpacing

    layout(layoutWidth, layoutHeight) {
      placeables.forEachIndexed { index, placeable ->
        val column = index % columnCount
        val row = (index - column) / columnCount

        placeable.placeRelative(
          x = leftPadding + (itemWidthPx + horizontalSpacing) * column,
          y = topBottomPadding + ((itemHeightPx + verticalSpacing) * row)
        )
      }
    }
  }
}